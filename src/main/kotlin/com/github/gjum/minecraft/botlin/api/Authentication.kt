package com.github.gjum.minecraft.botlin.api

import com.github.steveice10.mc.protocol.MinecraftProtocol

interface Authentication : Service {
    val defaultAccount: String

    /**
     * Usernames this module can authenticate.
     */
    val availableAccounts: Collection<String>

    fun authenticate(username: String): MinecraftProtocol?
}
