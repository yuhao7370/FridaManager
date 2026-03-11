package com.yuhao7370.fridamanager.data.remote

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

class GitHubReleaseApi(
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    suspend fun fetchFridaReleases(baseUrl: String): List<GitHubReleaseDto> {
        return runCatching {
            fetchPages(baseUrl, perPage = 100, maxPages = 12)
        }.recoverCatching { primaryError ->
            if (isGatewayOrTimeout(primaryError)) {
                // Fall back to smaller pages when the upstream is unstable, while still
                // walking enough history to surface older Frida major versions.
                fetchPages(baseUrl, perPage = 40, maxPages = 20)
            } else {
                throw primaryError
            }
        }.getOrElse { throwable ->
            throw normalizeFinalError(throwable)
        }
    }

    private suspend fun fetchPages(baseUrl: String, perPage: Int, maxPages: Int): List<GitHubReleaseDto> {
        val all = mutableListOf<GitHubReleaseDto>()
        for (page in 1..maxPages) {
            val pageItems = fetchPageWithRetry(baseUrl, perPage, page)
            if (pageItems.isEmpty()) break
            all += pageItems
            if (pageItems.size < perPage) break
        }
        return all.distinctBy { it.tagName }
    }

    private suspend fun fetchPageWithRetry(
        baseUrl: String,
        perPage: Int,
        page: Int
    ): List<GitHubReleaseDto> {
        val maxAttempts = 3
        var lastError: Throwable? = null
        for (attempt in 0 until maxAttempts) {
            try {
                return fetchPage(baseUrl, perPage, page)
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

    private suspend fun fetchPage(baseUrl: String, perPage: Int, page: Int): List<GitHubReleaseDto> {
        val normalized = baseUrl.trimEnd('/')
        val url = "$normalized/repos/frida/frida/releases?per_page=$perPage&page=$page"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "FridaManager/1.0")
            .build()

        return client.awaitResponse(request).use { response ->
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
            json.decodeFromString(ListSerializer(GitHubReleaseDto.serializer()), body)
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
}
