package com.yuhao7370.fridamanager.app

import android.app.Application
import com.topjohnwu.superuser.Shell

class FridaManagerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Verbose shell logging floods logcat with full root scripts and can stall the device.
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setContext(this)
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(15)
        )
        container = AppContainer(this)
    }
}
