package com.github.gjum.minecraft.botlin

import com.github.gjum.minecraft.botlin.util.parseCredentialsFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
