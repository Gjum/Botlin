package com.github.gjum.minecraft.botlin.modules.defaults

import kotlin.test.Test
import kotlin.test.assertEquals

class AuthTest {
    @Test
    fun `parses credentials file format`() {
        val spacedPw = " space before and after password "
        val emptyPw = ""
        val lines = listOf("notch@example.com $emptyPw", "", "jeb@example.com $spacedPw")
        val cred = parseCredentialsFile(lines)
        assertEquals(cred["notch@example.com"], emptyPw)
        assertEquals(cred["jeb@example.com"], spacedPw)
    }
}
