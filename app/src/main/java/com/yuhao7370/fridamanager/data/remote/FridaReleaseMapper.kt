package com.yuhao7370.fridamanager.data.remote

import com.yuhao7370.fridamanager.model.RemoteFridaAsset
import com.yuhao7370.fridamanager.model.RemoteFridaVersion

object FridaReleaseMapper {
    private val assetRegex =
        Regex("""frida-server-([0-9A-Za-z._-]+)-android-(arm64|arm|x86_64|x86)(\.xz)?$""")

    fun mapToRemoteVersions(releases: List<GitHubReleaseDto>): List<RemoteFridaVersion> {
        return releases
            .filterNot { it.draft }
            .mapNotNull { release ->
                val assets = release.assets.mapNotNull { parseAsset(it.name, it.browserDownloadUrl, it.size) }
                if (assets.isEmpty()) {
                    null
                } else {
                    RemoteFridaVersion(
                        version = normalizeVersion(release.tagName),
                        publishedAt = release.publishedAt,
                        assets = assets
                    )
                }
            }
    }

    fun parseAsset(name: String, url: String, size: Long): RemoteFridaAsset? {
        val match = assetRegex.find(name) ?: return null
        val abiShort = match.groupValues[2]
        return RemoteFridaAsset(
            name = name,
            downloadUrl = url,
            sizeBytes = size,
            abiTag = "android-$abiShort"
        )
    }

    fun extractVersionFromAssetName(fileName: String): String? {
        val match = assetRegex.find(fileName) ?: return null
        return normalizeVersion(match.groupValues[1])
    }

    fun normalizeVersion(raw: String): String = raw.removePrefix("v").trim()
}
