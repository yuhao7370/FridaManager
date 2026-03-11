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
        // Android 16 has upstream stability reports around running frida-server
        // (see frida/frida#3471 and frida/frida#3620). Combined with local reproduction
        // of severe instability on Frida 16.x, this app conservatively blocks Frida 16.x
        // on Android 16+. Frida 17+ is only a safer starting point, not a hard guarantee.
        if (sdkInt >= 36 && major < 17) {
            return FridaVersionSafetyDecision(
                allowed = false,
                message = "Frida $version is blocked on Android 16+ due to reported Android 16 stability issues. Start from Frida 17 or newer, then verify stability on your device."
            )
        }

        return FridaVersionSafetyDecision(allowed = true)
    }
}
