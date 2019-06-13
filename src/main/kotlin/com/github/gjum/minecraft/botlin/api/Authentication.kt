package com.github.gjum.minecraft.botlin.api

import com.github.steveice10.mc.protocol.MinecraftProtocol

interface Authentication : Service {
    val defaultAccount: String?

    /**
     * Usernames this module can authenticate.
     */
    val availableAccounts: Collection<String>

    /**
     * Try authenticating the [username], using various credentials sources,
     * for example .auth_tokens.json, .credentials, MINECRAFT_PASSWORD env.
     */
    suspend fun authenticate(username: String): MinecraftProtocol?
}
