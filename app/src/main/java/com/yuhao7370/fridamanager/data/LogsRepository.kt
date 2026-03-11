package com.yuhao7370.fridamanager.data

import com.yuhao7370.fridamanager.data.local.FridaFileLayout
import com.yuhao7370.fridamanager.model.LogSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.min

class LogsRepository(private val fileLayout: FridaFileLayout) {
    suspend fun readLog(source: LogSource, maxChars: Int = 120_000): String = withContext(Dispatchers.IO) {
        fileLayout.ensureInitialized()
        val file = fileForSource(source)
        if (!file.exists()) return@withContext ""
        readTail(file, maxChars)
    }

    suspend fun clear(source: LogSource?) = withContext(Dispatchers.IO) {
        fileLayout.ensureInitialized()
        if (source == null) {
            listOf(
                fileLayout.controllerLogFile,
                fileLayout.fridaStdoutLogFile,
                fileLayout.fridaStderrLogFile
            ).forEach { it.writeText("") }
        } else {
            fileForSource(source).writeText("")
        }
    }

    suspend fun trimToRetention(maxKb: Int) = withContext(Dispatchers.IO) {
        val maxBytes = maxKb.coerceAtLeast(64) * 1024
        listOf(
            fileLayout.controllerLogFile,
            fileLayout.fridaStdoutLogFile,
            fileLayout.fridaStderrLogFile
        ).forEach { file ->
            if (!file.exists()) return@forEach
            if (file.length() <= maxBytes) return@forEach
            val text = file.readText()
            val trimmed = if (text.length > maxBytes) text.takeLast(maxBytes) else text
            file.writeText(trimmed)
        }
    }

    fun fileForSource(source: LogSource): File = when (source) {
        LogSource.CONTROLLER -> fileLayout.controllerLogFile
        LogSource.FRIDA_STDOUT -> fileLayout.fridaStdoutLogFile
        LogSource.FRIDA_STDERR -> fileLayout.fridaStderrLogFile
    }

    private fun readTail(file: File, maxChars: Int): String {
        if (!file.exists()) return ""
        val maxBytes = maxChars * 4L
        RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            val readSize = min(length, maxBytes).toInt()
            val buffer = ByteArray(readSize)
            raf.seek(length - readSize)
            raf.readFully(buffer)
            return buffer.toString(Charsets.UTF_8).takeLast(maxChars)
        }
    }
}
