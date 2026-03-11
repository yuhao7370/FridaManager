package com.yuhao7370.fridamanager.domain

import com.google.common.truth.Truth.assertThat
import com.yuhao7370.fridamanager.model.AbiMapper
import org.junit.Test

class AbiMapperTest {
    @Test
    fun `maps arm64 variants to android-arm64`() {
        assertThat(AbiMapper.toFridaTag("arm64-v8a")).isEqualTo("android-arm64")
        assertThat(AbiMapper.toFridaTag("aarch64")).isEqualTo("android-arm64")
    }

    @Test
    fun `maps arm variants to android-arm`() {
        assertThat(AbiMapper.toFridaTag("armeabi-v7a")).isEqualTo("android-arm")
        assertThat(AbiMapper.toFridaTag("armv7l")).isEqualTo("android-arm")
    }

    @Test
    fun `returns null for unknown abi`() {
        assertThat(AbiMapper.toFridaTag("mips64")).isNull()
    }
}
