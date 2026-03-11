package com.yuhao7370.fridamanager.data.local

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ControllerLogger(private val fileLayout: FridaFileLayout) {
    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    fun log(message: String) {
        fileLayout.ensureInitialized()
        val line = "[${format.format(Date())}] $message\n"
        fileLayout.controllerLogFile.appendText(line)
    }
}
