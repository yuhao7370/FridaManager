package com.yuhao7370.fridamanager.data.remote

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InterruptedIOException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

private class HttpStatusException(
    val code: Int,
    val retryable: Boolean,
    message: String
) : IOException(message)

sealed interface GitHubReleaseFetchResult {
    data class Updated(
        val releases: List<GitHubReleaseDto>,
        val etag: String?
    ) : GitHubReleaseFetchResult

    data object NotModified : GitHubReleaseFetchResult
}

data class GitHubReleaseFetchProgress(
    val message: String,
    val loadedPages: Int = 0,
    val totalPages: Int = 0,
    val loadedReleases: Int = 0
)

private data class GitHubReleasePageResult(
    val releases: List<GitHubReleaseDto>,
    val etag: String?,
    val notModified: Boolean
)

class GitHubReleaseApi(
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    suspend fun fetchFridaReleases(
        baseUrl: String,
        cachedEtag: String? = null,
        knownVersions: Set<String> = emptySet(),
        onProgress: (GitHubReleaseFetchProgress) -> Unit = {}
    ): GitHubReleaseFetchResult {
        val hasCache = cachedEtag != null || knownVersions.isNotEmpty()
        return runCatching {
            fetchPages(
                baseUrl = baseUrl,
                perPage = 100,
                maxPages = if (hasCache) 2 else 4,
                cachedEtag = cachedEtag,
                knownVersions = knownVersions,
                onProgress = onProgress
            )
        }.recoverCatching { primaryError ->
            if (isGatewayOrTimeout(primaryError)) {
                fetchPages(
                    baseUrl = baseUrl,
                    perPage = 40,
                    maxPages = if (hasCache) 3 else 5,
                    cachedEtag = cachedEtag,
                    knownVersions = knownVersions,
                    onProgress = onProgress
                )
            } else {
                throw primaryError
            }
        }.getOrElse { throwable ->
            throw normalizeFinalError(throwable)
        }
    }

    suspend fun fetchReleaseByTag(baseUrl: String, version: String): GitHubReleaseDto? {
        val normalizedVersion = normalizeTag(version)
        val candidates = linkedSetOf(
            version.trim(),
            normalizedVersion,
            "v$normalizedVersion"
        ).filter { it.isNotBlank() }

        var lastError: Throwable? = null
        candidates.forEach { candidate ->
            try {
                return fetchReleaseByExactTag(baseUrl, candidate)
            } catch (error: HttpStatusException) {
                if (error.code == 404) return@forEach
                lastError = error
                throw normalizeFinalError(error)
            } catch (error: Throwable) {
                lastError = error
                throw normalizeFinalError(error)
            }
        }
        if (lastError != null) throw normalizeFinalError(lastError!!)
        return null
    }

    private suspend fun fetchPages(
        baseUrl: String,
        perPage: Int,
        maxPages: Int,
        cachedEtag: String?,
        knownVersions: Set<String>,
        onProgress: (GitHubReleaseFetchProgress) -> Unit
    ): GitHubReleaseFetchResult = coroutineScope {
        val parallelInitialFetch = cachedEtag.isNullOrBlank() && knownVersions.isEmpty() && maxPages > 1
        if (parallelInitialFetch) {
            onProgress(
                GitHubReleaseFetchProgress(
                    message = "Requesting pages 1/$maxPages",
                    loadedPages = 0,
                    totalPages = maxPages,
                    loadedReleases = 0
                )
            )

            val pageResults = (1..maxPages).map { page ->
                async {
                    page to fetchPageWithRetry(
                        baseUrl = baseUrl,
                        perPage = perPage,
                        page = page,
                        cachedEtag = null
                    )
                }
            }.awaitAll().sortedBy { it.first }

            val all = mutableListOf<GitHubReleaseDto>()
            var etag: String? = null
            for ((page, pageResult) in pageResults) {
                if (page == 1) {
                    etag = pageResult.etag
                }
                val pageItems = pageResult.releases
                if (pageItems.isEmpty()) break
                all += pageItems
                onProgress(
                    GitHubReleaseFetchProgress(
                        message = "Loaded page $page/$maxPages",
                        loadedPages = page,
                        totalPages = maxPages,
                        loadedReleases = all.size
                    )
                )
                if (pageItems.size < perPage) break
            }

            return@coroutineScope GitHubReleaseFetchResult.Updated(
                releases = all.distinctBy { normalizeTag(it.tagName) },
                etag = etag
            )
        }

        onProgress(
            GitHubReleaseFetchProgress(
                message = "Requesting page 1/$maxPages",
                loadedPages = 0,
                totalPages = maxPages,
                loadedReleases = 0
            )
        )
        val firstPageResult = fetchPageWithRetry(
            baseUrl = baseUrl,
            perPage = perPage,
            page = 1,
            cachedEtag = cachedEtag
        )
        if (firstPageResult.notModified) {
            onProgress(
                GitHubReleaseFetchProgress(
                    message = "Remote not modified, using cache",
                    loadedPages = 1,
                    totalPages = maxPages,
                    loadedReleases = 0
                )
            )
            return@coroutineScope GitHubReleaseFetchResult.NotModified
        }

        val all = mutableListOf<GitHubReleaseDto>()
        all += firstPageResult.releases
        onProgress(
            GitHubReleaseFetchProgress(
                message = "Loaded page 1/$maxPages",
                loadedPages = 1,
                totalPages = maxPages,
                loadedReleases = all.size
            )
        )

        if (firstPageResult.releases.isEmpty() || firstPageResult.releases.size < perPage || maxPages == 1) {
            return@coroutineScope GitHubReleaseFetchResult.Updated(
                releases = all.distinctBy { normalizeTag(it.tagName) },
                etag = firstPageResult.etag
            )
        }

        val rest = (2..maxPages).map { page ->
            async {
                onProgress(
                    GitHubReleaseFetchProgress(
                        message = "Requesting page $page/$maxPages",
                        loadedPages = 1,
                        totalPages = maxPages,
                        loadedReleases = all.size
                    )
                )
                page to fetchPageWithRetry(
                    baseUrl = baseUrl,
                    perPage = perPage,
                    page = page,
                    cachedEtag = null
                )
            }
        }.awaitAll().sortedBy { it.first }

        for ((page, pageResult) in rest) {
            val pageItems = pageResult.releases
            if (pageItems.isEmpty()) break
            all += pageItems
            onProgress(
                GitHubReleaseFetchProgress(
                    message = "Loaded page $page/$maxPages",
                    loadedPages = page,
                    totalPages = maxPages,
                    loadedReleases = all.size
                )
            )
            if (knownVersions.isNotEmpty() && pageItems.all { normalizeTag(it.tagName) in knownVersions }) {
                onProgress(
                    GitHubReleaseFetchProgress(
                        message = "Reached cached history boundary at page $page",
                        loadedPages = page,
                        totalPages = maxPages,
                        loadedReleases = all.size
                    )
                )
                break
            }
            if (pageItems.size < perPage) break
        }

        GitHubReleaseFetchResult.Updated(
            releases = all.distinctBy { normalizeTag(it.tagName) },
            etag = firstPageResult.etag
        )
    }

    private suspend fun fetchPageWithRetry(
        baseUrl: String,
        perPage: Int,
        page: Int,
        cachedEtag: String?
    ): GitHubReleasePageResult {
        val maxAttempts = 3
        var lastError: Throwable? = null
        for (attempt in 0 until maxAttempts) {
            try {
                return fetchPage(baseUrl, perPage, page, cachedEtag)
            } catch (t: Throwable) {
                lastError = t
                val shouldRetry = shouldRetryFetch(t)
                val lastAttempt = attempt == maxAttempts - 1
                if (!shouldRetry || lastAttempt) break
                delay(700L * (attempt + 1))
            }
        }
        throw lastError ?: IOException("GitHub API request failed")
    }

    private suspend fun fetchPage(
        baseUrl: String,
        perPage: Int,
        page: Int,
        cachedEtag: String?
    ): GitHubReleasePageResult {
        val normalized = baseUrl.trimEnd('/')
        val url = "$normalized/repos/frida/frida/releases?per_page=$perPage&page=$page"
        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "FridaManager/1.0")
        if (!cachedEtag.isNullOrBlank()) {
            requestBuilder.header("If-None-Match", cachedEtag)
        }
        val request = requestBuilder.build()

        return client.awaitResponse(request).use { response ->
            if (response.code == 304) {
                return@use GitHubReleasePageResult(
                    releases = emptyList(),
                    etag = response.header("ETag"),
                    notModified = true
                )
            }
            if (!response.isSuccessful) {
                val code = response.code
                val retryable = code in 500..599 || code == 429
                throw HttpStatusException(
                    code = code,
                    retryable = retryable,
                    message = "GitHub API request failed: HTTP $code"
                )
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) {
                throw IOException("GitHub API returned empty response")
            }
            GitHubReleasePageResult(
                releases = json.decodeFromString(ListSerializer(GitHubReleaseDto.serializer()), body),
                etag = response.header("ETag"),
                notModified = false
            )
        }
    }

    private suspend fun fetchReleaseByExactTag(baseUrl: String, tag: String): GitHubReleaseDto {
        val normalized = baseUrl.trimEnd('/')
        val url = "$normalized/repos/frida/frida/releases/tags/$tag"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "FridaManager/1.0")
            .build()

        return client.awaitResponse(request).use { response ->
            if (!response.isSuccessful) {
                val code = response.code
                throw HttpStatusException(
                    code = code,
                    retryable = code in 500..599 || code == 429,
                    message = "GitHub API request failed: HTTP $code"
                )
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) {
                throw IOException("GitHub API returned empty response")
            }
            json.decodeFromString(GitHubReleaseDto.serializer(), body)
        }
    }

    private fun isGatewayOrTimeout(error: Throwable): Boolean {
        return when (error) {
            is SocketTimeoutException -> true
            is InterruptedIOException -> true
            is HttpStatusException -> error.code == 504
            else -> false
        }
    }

    private fun normalizeFinalError(error: Throwable): IOException {
        if (error is SocketTimeoutException || error is InterruptedIOException) {
            return IOException("GitHub request timed out. Check network or set mirror API URL in settings.", error)
        }
        if (error is HttpStatusException && error.code == 504) {
            return IOException("GitHub API gateway timeout (HTTP 504). Try again or use a mirror API URL.", error)
        }
        return IOException(error.message ?: "GitHub API request failed", error)
    }

    private fun shouldRetryFetch(error: Throwable): Boolean {
        return when (error) {
            is SocketTimeoutException -> true
            is InterruptedIOException -> true
            is UnknownHostException -> true
            is HttpStatusException -> error.retryable
            is IOException -> true
            else -> false
        }
    }

    private fun normalizeTag(raw: String): String {
        return raw.trim().removePrefix("v").removePrefix("V")
    }
}
