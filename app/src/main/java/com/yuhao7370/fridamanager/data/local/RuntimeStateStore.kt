package com.yuhao7370.fridamanager.data.local

import com.yuhao7370.fridamanager.model.RuntimeState
import kotlinx.serialization.json.Json
import java.io.IOException

class RuntimeStateStore(
    private val fileLayout: FridaFileLayout,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }
) {
    fun read(): RuntimeState? = runCatching {
        fileLayout.ensureInitialized()
        if (!fileLayout.statusFile.exists()) return null
        json.decodeFromString<RuntimeState>(fileLayout.statusFile.readText())
    }.getOrNull()

    fun write(state: RuntimeState) {
        fileLayout.ensureInitialized()
        val payload = json.encodeToString(RuntimeState.serializer(), state)
        fileLayout.statusFile.writeText(payload)
        state.lastError?.let { error ->
            fileLayout.lastErrorFile.writeText("${error.type}: ${error.message.orEmpty()}")
        }
    }

    @Throws(IOException::class)
    fun writeCurrentVersion(version: String?) {
        fileLayout.ensureInitialized()
        if (version.isNullOrBlank()) {
            if (fileLayout.currentVersionFile.exists()) fileLayout.currentVersionFile.delete()
            return
        }
        fileLayout.currentVersionFile.writeText(version)
    }

    fun readCurrentVersion(): String? = runCatching {
        fileLayout.ensureInitialized()
        if (!fileLayout.currentVersionFile.exists()) return null
        fileLayout.currentVersionFile.readText().trim().takeIf { it.isNotBlank() }
    }.getOrNull()
}
