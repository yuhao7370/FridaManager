package com.yuhao7370.fridamanager.root

import com.yuhao7370.fridamanager.data.local.FridaFileLayout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ManagedProcessHandle(
    val pid: Int,
    val startTimeTicks: Long,
    val version: String,
    val binaryPath: String,
    val host: String,
    val port: Int,
    val launchedAtMs: Long = System.currentTimeMillis()
)

class ProcessHandleStore(
    private val fileLayout: FridaFileLayout,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }
) {
    fun read(): ManagedProcessHandle? = runCatching {
        val file = fileLayout.processHandleFile
        if (!file.exists()) return null
        json.decodeFromString<ManagedProcessHandle>(file.readText())
    }.getOrNull()

    fun write(handle: ManagedProcessHandle) {
        fileLayout.ensureInitialized()
        fileLayout.processHandleFile.writeText(
            json.encodeToString(ManagedProcessHandle.serializer(), handle)
        )
        fileLayout.pidFile.writeText(handle.pid.toString())
    }

    fun clear() {
        if (fileLayout.processHandleFile.exists()) {
            fileLayout.processHandleFile.delete()
        }
        if (fileLayout.pidFile.exists()) {
            fileLayout.pidFile.delete()
        }
    }
}
