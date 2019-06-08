package com.github.gjum.minecraft.botlin.api

interface AvatarService : Service {
    fun getAvatar(username: String): Avatar
}
