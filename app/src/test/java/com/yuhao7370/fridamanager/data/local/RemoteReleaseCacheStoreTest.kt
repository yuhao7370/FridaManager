package com.yuhao7370.fridamanager.data.local

import com.google.common.truth.Truth.assertThat
import com.yuhao7370.fridamanager.model.RemoteFridaAsset
import com.yuhao7370.fridamanager.model.RemoteFridaVersion
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RemoteReleaseCacheStoreTest {
    @Test
    fun `save then load with same base url returns cached versions`() = runBlocking {
        val tempDir = Files.createTempDirectory("remote-cache-test").toFile()
        try {
            val cacheFile = File(tempDir, "remote_releases.json")
            val store = RemoteReleaseCacheStore(cacheFile)
            val versions = sampleVersions("17.8.0")

            store.save("https://api.github.com/", versions)

            val loaded = store.load("https://api.github.com")
            assertThat(loaded).isEqualTo(versions)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `load falls back to latest cache when base url differs`() = runBlocking {
        val tempDir = Files.createTempDirectory("remote-cache-test").toFile()
        try {
            val cacheFile = File(tempDir, "remote_releases.json")
            val store = RemoteReleaseCacheStore(cacheFile)
            val versions = sampleVersions("16.4.9")

            store.save("https://api.github.com", versions)

            val loaded = store.load("https://example-mirror.invalid")
            assertThat(loaded).isEqualTo(versions)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun sampleVersions(version: String): List<RemoteFridaVersion> {
        return listOf(
            RemoteFridaVersion(
                version = version,
                publishedAt = "2026-01-01T00:00:00Z",
                assets = listOf(
                    RemoteFridaAsset(
                        name = "frida-server-$version-android-arm64.xz",
                        downloadUrl = "https://example.com/frida-server-$version-android-arm64.xz",
                        sizeBytes = 1024,
                        abiTag = "android-arm64"
                    )
                )
            )
        )
    }
}
