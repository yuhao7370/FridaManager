package com.yuhao7370.fridamanager.data.local

import com.yuhao7370.fridamanager.model.RemoteFridaVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

class RemoteReleaseCacheStore(
    private val cacheFile: File,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
) {
    data class Snapshot(
        val baseUrl: String,
        val savedAtMs: Long,
        val versions: List<RemoteFridaVersion>,
        val etag: String?
    )

    suspend fun save(baseUrl: String, versions: List<RemoteFridaVersion>, etag: String? = null) =
        withContext(Dispatchers.IO) {
        if (versions.isEmpty()) return@withContext
        val payload = readPayload()
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        val updatedEntries = payload.entries
            .filterNot { normalizeBaseUrl(it.baseUrl) == normalizedBaseUrl }
            .plus(
                RemoteReleaseCacheEntry(
                    baseUrl = normalizedBaseUrl,
                    savedAtMs = System.currentTimeMillis(),
                    versions = versions,
                    etag = etag
                )
            )
            .sortedByDescending { it.savedAtMs }
            .take(MAX_ENTRIES)

        writePayload(RemoteReleaseCachePayload(updatedEntries))
    }

    suspend fun load(baseUrl: String): List<RemoteFridaVersion>? = withContext(Dispatchers.IO) {
        loadSnapshot(baseUrl)?.versions
    }

    suspend fun loadExactSnapshot(baseUrl: String): Snapshot? = withContext(Dispatchers.IO) {
        val payload = readPayload()
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        payload.entries
            .firstOrNull { normalizeBaseUrl(it.baseUrl) == normalizedBaseUrl && it.versions.isNotEmpty() }
            ?.toSnapshot()
    }

    suspend fun loadSnapshot(baseUrl: String): Snapshot? = withContext(Dispatchers.IO) {
        val payload = readPayload()
        if (payload.entries.isEmpty()) return@withContext null

        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        val exact = payload.entries.firstOrNull {
            normalizeBaseUrl(it.baseUrl) == normalizedBaseUrl && it.versions.isNotEmpty()
        }
        if (exact != null) return@withContext exact.toSnapshot()

        payload.entries.firstOrNull { it.versions.isNotEmpty() }?.toSnapshot()
    }

    private fun readPayload(): RemoteReleaseCachePayload {
        if (!cacheFile.exists() || cacheFile.length() <= 0L) return RemoteReleaseCachePayload()
        return runCatching {
            json.decodeFromString(RemoteReleaseCachePayload.serializer(), cacheFile.readText())
        }.getOrElse {
            RemoteReleaseCachePayload()
        }
    }

    private fun writePayload(payload: RemoteReleaseCachePayload) {
        cacheFile.parentFile?.mkdirs()
        val tempFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
        tempFile.writeText(json.encodeToString(RemoteReleaseCachePayload.serializer(), payload))
        if (!tempFile.renameTo(cacheFile)) {
            tempFile.copyTo(cacheFile, overwrite = true)
            tempFile.delete()
        }
    }

    private fun normalizeBaseUrl(url: String): String {
        return url.trim().trimEnd('/')
    }

    @Serializable
    private data class RemoteReleaseCachePayload(
        val entries: List<RemoteReleaseCacheEntry> = emptyList()
    )

    @Serializable
    private data class RemoteReleaseCacheEntry(
        val baseUrl: String,
        val savedAtMs: Long,
        val versions: List<RemoteFridaVersion>,
        val etag: String? = null
    )

    private fun RemoteReleaseCacheEntry.toSnapshot(): Snapshot {
        return Snapshot(
            baseUrl = baseUrl,
            savedAtMs = savedAtMs,
            versions = versions,
            etag = etag
        )
    }

    private companion object {
        const val MAX_ENTRIES = 6
    }
}
