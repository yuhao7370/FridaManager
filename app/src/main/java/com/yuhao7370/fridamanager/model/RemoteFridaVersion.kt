package com.yuhao7370.fridamanager.model

import kotlinx.serialization.Serializable

@Serializable
data class RemoteFridaAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val abiTag: String?
)

@Serializable
data class RemoteFridaVersion(
    val version: String,
    val publishedAt: String,
    val assets: List<RemoteFridaAsset>
) {
    fun matchingAsset(abiTag: String?): RemoteFridaAsset? {
        if (abiTag == null) return null
        return assets.firstOrNull { it.abiTag == abiTag }
    }
}
