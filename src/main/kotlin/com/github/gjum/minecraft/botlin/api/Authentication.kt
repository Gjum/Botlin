package com.github.gjum.minecraft.botlin.api

import com.github.steveice10.mc.protocol.MinecraftProtocol

interface Authentication : Service {
    suspend fun authenticate(): MinecraftProtocol?
}
