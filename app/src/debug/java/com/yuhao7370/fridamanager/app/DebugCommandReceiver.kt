package com.yuhao7370.fridamanager.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.yuhao7370.fridamanager.model.AppResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DebugCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val app = context.applicationContext as? FridaManagerApp ?: return
        val action = intent?.action ?: return
        val tag = "FridaDebugReceiver"
        val pending = goAsync()
        Log.i(tag, "Received action=$action")

        app.container.appScope.launch {
            val message = runCatching {
                when (action) {
                    ACTION_START -> {
                        val settings = app.container.observeSettingsUseCase().first()
                        when (val result = app.container.startFridaServerUseCase(
                            settings.defaultHost,
                            settings.defaultPort
                        )) {
                            is AppResult.Success -> "START ok pid=${result.data.pid} status=${result.data.status}"
                            is AppResult.Failure -> "START fail ${result.error.type} ${result.error.message.orEmpty()}"
                        }
                    }

                    ACTION_STOP -> {
                        when (val result = app.container.stopFridaServerUseCase()) {
                            is AppResult.Success -> "STOP ok status=${result.data.status}"
                            is AppResult.Failure -> "STOP fail ${result.error.type} ${result.error.message.orEmpty()}"
                        }
                    }

                    ACTION_RESTART -> {
                        val settings = app.container.observeSettingsUseCase().first()
                        when (val result = app.container.restartFridaServerUseCase(
                            settings.defaultHost,
                            settings.defaultPort
                        )) {
                            is AppResult.Success -> "RESTART ok pid=${result.data.pid} status=${result.data.status}"
                            is AppResult.Failure -> "RESTART fail ${result.error.type} ${result.error.message.orEmpty()}"
                        }
                    }

                    ACTION_SWITCH -> {
                        val settings = app.container.observeSettingsUseCase().first()
                        val version = intent?.getStringExtra(EXTRA_VERSION).orEmpty()
                        when (val result = app.container.switchActiveFridaVersionUseCase(
                            version,
                            settings.defaultHost,
                            settings.defaultPort
                        )) {
                            is AppResult.Success -> "SWITCH ok version=$version"
                            is AppResult.Failure -> "SWITCH fail ${result.error.type} ${result.error.message.orEmpty()}"
                        }
                    }

                    ACTION_STATUS -> {
                        val result = app.container.refreshRuntimeStatusUseCase()
                        "STATUS status=${result.status} pid=${result.pid} version=${result.activeVersion.orEmpty()}"
                    }

                    else -> "UNKNOWN_ACTION $action"
                }
            }.getOrElse { throwable ->
                "COMMAND fail ${throwable::class.java.simpleName} ${throwable.message.orEmpty()}"
            }

            Log.i(tag, message)
            pending.setResultCode(-1)
            pending.setResultData(message)
            pending.finish()
        }
    }

    companion object {
        private const val ACTION_START = "com.yuhao7370.fridamanager.DEBUG_START"
        private const val ACTION_STOP = "com.yuhao7370.fridamanager.DEBUG_STOP"
        private const val ACTION_RESTART = "com.yuhao7370.fridamanager.DEBUG_RESTART"
        private const val ACTION_SWITCH = "com.yuhao7370.fridamanager.DEBUG_SWITCH"
        private const val ACTION_STATUS = "com.yuhao7370.fridamanager.DEBUG_STATUS"
        private const val EXTRA_VERSION = "version"
    }
}
