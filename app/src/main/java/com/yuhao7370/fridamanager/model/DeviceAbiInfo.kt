package com.yuhao7370.fridamanager.model

data class DeviceAbiInfo(
    val primaryAbi: String?,
    val abiList: List<String>,
    val unameArch: String?,
    val fridaAssetTag: String?,
    val supportedFridaTags: List<String>
)

object AbiMapper {
    fun toFridaTag(abi: String?): String? = when (abi?.lowercase()) {
        "arm64-v8a", "aarch64", "arm64" -> "android-arm64"
        "armeabi-v7a", "armeabi", "armv7l", "arm" -> "android-arm"
        "x86_64", "amd64" -> "android-x86_64"
        "x86", "i686", "i386" -> "android-x86"
        else -> null
    }
}
