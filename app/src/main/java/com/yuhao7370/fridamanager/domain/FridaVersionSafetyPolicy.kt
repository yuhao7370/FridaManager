package com.yuhao7370.fridamanager.domain

import android.os.Build

data class FridaVersionSafetyDecision(
    val allowed: Boolean,
    val message: String? = null
)

object FridaVersionSafetyPolicy {
    fun evaluate(version: String, sdkInt: Int = Build.VERSION.SDK_INT): FridaVersionSafetyDecision {
        val major = version.substringBefore('.').toIntOrNull()
            ?: return FridaVersionSafetyDecision(allowed = true)

        // Practical guardrail:
        // On this Android 16 / HyperOS 3 device family, Frida 16.x reproduced a system_server crash
        // during runtime/stop. Blocking older major versions is safer than allowing device reboots.
        if (sdkInt >= 36 && major < 17) {
            return FridaVersionSafetyDecision(
                allowed = false,
                message = "Frida $version is blocked on Android 16+ because this device reproduced system_server crashes with Frida 16.x. Use Frida 17 or newer."
            )
        }

        return FridaVersionSafetyDecision(allowed = true)
    }
}
