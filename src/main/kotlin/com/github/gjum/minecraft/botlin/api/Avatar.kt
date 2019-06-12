package com.github.gjum.minecraft.botlin.api

import com.github.gjum.minecraft.botlin.state.*
import com.github.gjum.minecraft.botlin.util.Look
import com.github.gjum.minecraft.botlin.util.Vec3d
import com.github.steveice10.mc.auth.data.GameProfile
import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode
import com.github.steveice10.packetlib.Session
import java.util.UUID

/**
 * An enhanced client that tracks world state,
 * handles disconnection, and can be reconnected.
 */
interface Avatar: EventEmitter<AvatarEvents> {
    val profile: GameProfile?
    val connection: Session?
    val endReason: String?
    val entity: Entity?
    val health: Float?
    val food: Int?
    val saturation: Float?
    val experience: Experience?
    val inventory: Window?
    val position: Vec3d? get() = entity?.position
    val look: Look? get() = entity?.look
    val onGround: Boolean? get() = entity?.onGround
    val gameMode: GameMode?

    var world: World?
    val playerList: Map<UUID, PlayerListItem>?

    fun useConnection(connection: Session, profile: GameProfile)

    /**
     * Disconnects the client, blocking the current thread.
     */
    fun disconnect(reason: String?, cause: Throwable? = null)

    val connected get() = connection != null && endReason == null && profile != null
    val spawned: Boolean
        get() = (position != null
            && entity != null
            && health != null
            && experience != null
            && connected)
    val alive get() = health ?: 0.0f > 0.0f && spawned
}
