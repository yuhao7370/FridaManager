package com.yuhao7370.fridamanager.data

import android.os.Build
import com.yuhao7370.fridamanager.model.AbiMapper
import com.yuhao7370.fridamanager.model.DeviceAbiInfo
import com.yuhao7370.fridamanager.root.RootShellManager

class AbiRepository(private val shell: RootShellManager) {
    suspend fun detectDeviceAbi(): DeviceAbiInfo {
        val primary = Build.SUPPORTED_ABIS.firstOrNull()
        val abiList = Build.SUPPORTED_ABIS.toList()
        val uname = shell.exec("uname -m").stdout.lineSequence().firstOrNull()?.trim()
        val primaryTag = AbiMapper.toFridaTag(primary) ?: AbiMapper.toFridaTag(uname)
        val tags = buildSet {
            abiList.mapNotNullTo(this) { AbiMapper.toFridaTag(it) }
            AbiMapper.toFridaTag(uname)?.let(::add)
        }.toList()

        return DeviceAbiInfo(
            primaryAbi = primary,
            abiList = abiList,
            unameArch = uname,
            fridaAssetTag = primaryTag,
            supportedFridaTags = tags
        )
    }
}
