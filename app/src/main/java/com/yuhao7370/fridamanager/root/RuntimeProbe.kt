package com.yuhao7370.fridamanager.root

import java.net.InetSocketAddress
import java.net.Socket

data class RuntimeProbeResult(
    val isRunning: Boolean,
    val pid: Int?,
    val portListening: Boolean
)

class RuntimeProbe(private val processController: FridaProcessController) {
    suspend fun probe(host: String, port: Int, verifyPort: Boolean = false): RuntimeProbeResult {
        val status = processController.status()
        val listening = if (verifyPort && status.isRunning) {
            isPortListening(host, port)
        } else {
            false
        }
        return RuntimeProbeResult(
            isRunning = status.isRunning,
            pid = status.pid,
            portListening = listening
        )
    }

    private fun isPortListening(host: String, port: Int): Boolean {
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), PORT_CHECK_TIMEOUT_MS)
                true
            }
        }.getOrDefault(false)
    }

    companion object {
        private const val PORT_CHECK_TIMEOUT_MS = 250
    }
}
