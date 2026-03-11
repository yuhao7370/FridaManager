package com.yuhao7370.fridamanager.data.remote

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FridaReleaseMapperTest {
    @Test
    fun `maps release assets to remote versions`() {
        val releases = listOf(
            GitHubReleaseDto(
                tagName = "v16.4.8",
                publishedAt = "2026-01-01T00:00:00Z",
                assets = listOf(
                    GitHubAssetDto(
                        name = "frida-server-16.4.8-android-arm64.xz",
                        browserDownloadUrl = "https://example.com/arm64.xz",
                        size = 1234
                    ),
                    GitHubAssetDto(
                        name = "frida-server-16.4.8-android-x86_64.xz",
                        browserDownloadUrl = "https://example.com/x86_64.xz",
                        size = 2234
                    ),
                    GitHubAssetDto(
                        name = "frida-python-16.4.8.tar.xz",
                        browserDownloadUrl = "https://example.com/ignore.tar.xz",
                        size = 1
                    )
                )
            )
        )

        val mapped = FridaReleaseMapper.mapToRemoteVersions(releases)

        assertThat(mapped).hasSize(1)
        assertThat(mapped.first().version).isEqualTo("16.4.8")
        assertThat(mapped.first().assets.map { it.abiTag }).containsExactly("android-arm64", "android-x86_64")
    }

    @Test
    fun `extracts version from asset name`() {
        assertThat(
            FridaReleaseMapper.extractVersionFromAssetName("frida-server-17.0.1-android-arm.xz")
        ).isEqualTo("17.0.1")
    }
}
