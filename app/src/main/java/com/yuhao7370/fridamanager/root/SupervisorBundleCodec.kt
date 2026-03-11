package com.yuhao7370.fridamanager.root

import android.os.Bundle
import androidx.core.os.bundleOf

internal object SupervisorBundleCodec {
    private const val KEY_IS_RUNNING = "is_running"
    private const val KEY_PID = "pid"
    private const val KEY_MESSAGE = "message"

    fun encode(info: ProcessInfo): Bundle = bundleOf(
        KEY_IS_RUNNING to info.isRunning,
        KEY_PID to info.pid,
        KEY_MESSAGE to info.message
    )

    fun decode(bundle: Bundle): ProcessInfo {
        val classLoader = ProcessInfo::class.java.classLoader
        if (classLoader != null) {
            bundle.classLoader = classLoader
        }
        return ProcessInfo(
            isRunning = bundle.getBoolean(KEY_IS_RUNNING, false),
            pid = bundle.getInt(KEY_PID).takeIf { bundle.containsKey(KEY_PID) },
            message = bundle.getString(KEY_MESSAGE)
        )
    }
}
