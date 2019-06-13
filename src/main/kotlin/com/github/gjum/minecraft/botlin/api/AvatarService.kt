package com.github.gjum.minecraft.botlin.api

/**
 * Keeps track of the state ("[Avatar]") of multiple accounts on multiple servers.
 */
interface AvatarService : Service {
    /**
     * Returns the avatar and its associated state on the given account and server.
     * Creates an [Avatar] instance if necessary.
     */
    suspend fun getAvatar(username: String, serverAddress: String): Avatar
}
