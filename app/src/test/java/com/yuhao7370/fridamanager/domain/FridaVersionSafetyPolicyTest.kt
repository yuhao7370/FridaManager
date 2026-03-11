package com.yuhao7370.fridamanager.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FridaVersionSafetyPolicyTest {
    @Test
    fun `blocks Frida 16 on Android 16 and newer`() {
        val decision = FridaVersionSafetyPolicy.evaluate(version = "16.4.8", sdkInt = 36)

        assertThat(decision.allowed).isFalse()
        assertThat(decision.message).contains("Android 16+")
    }

    @Test
    fun `allows Frida 17 on Android 16 and newer`() {
        val decision = FridaVersionSafetyPolicy.evaluate(version = "17.8.0", sdkInt = 36)

        assertThat(decision.allowed).isTrue()
    }

    @Test
    fun `allows Frida 16 on older Android releases`() {
        val decision = FridaVersionSafetyPolicy.evaluate(version = "16.4.8", sdkInt = 35)

        assertThat(decision.allowed).isTrue()
    }
}
