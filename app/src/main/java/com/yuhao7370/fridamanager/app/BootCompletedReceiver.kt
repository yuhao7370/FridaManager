package com.yuhao7370.fridamanager.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            return
        }

        val app = context.applicationContext as? FridaManagerApp ?: return
        val pending = goAsync()
        app.container.appScope.launch {
            runCatching {
                val settings = app.container.observeSettingsUseCase().first()
                if (settings.autoStart) {
                    app.container.startFridaServerUseCase(settings.defaultHost, settings.defaultPort)
                }
            }
            pending.finish()
        }
    }
}
