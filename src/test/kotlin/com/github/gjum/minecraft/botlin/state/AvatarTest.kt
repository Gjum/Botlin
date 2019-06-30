package com.github.gjum.minecraft.botlin.state

import com.github.gjum.minecraft.botlin.defaults.AvatarImpl
import com.github.steveice10.mc.auth.data.GameProfile
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals

class AvatarTest {
    @Test
    fun `server address is normalized`() {
        val profile = mockk<GameProfile>()
        fun normalize(serverAddr: String) = AvatarImpl(profile, serverAddr).serverAddress

        assertEquals("some.host:25565", normalize("some.host"))
        assertEquals("localhost:25565", normalize("localhost"))
        assertEquals("example.com:4242", normalize("example.com:4242"))
        assertEquals("localhost:12321", normalize("localhost:12321"))
    }
}
