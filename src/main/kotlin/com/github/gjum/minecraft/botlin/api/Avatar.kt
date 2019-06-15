package com.github.gjum.minecraft.botlin.api

import com.github.gjum.minecraft.botlin.state.*
import com.github.gjum.minecraft.botlin.util.Look
import com.github.gjum.minecraft.botlin.util.Vec3d
import com.github.steveice10.mc.auth.data.GameProfile
import com.github.steveice10.mc.protocol.MinecraftProtocol
import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode
import com.github.steveice10.packetlib.Session
import java.util.UUID

/**
 * State (embodiment) of an account on one server,
 * uniquely identified by [profile] and [serverAddress].
 */
interface Avatar : EventEmitter<AvatarEvents> {
    val identifier get() = "${profile.name}@$serverAddress"

    val profile: GameProfile

    /** Normalized to format "host:port". */
    val serverAddress: String

    val behavior: BehaviorInstance
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

    val world: World?
    val playerList: Map<UUID, PlayerListItem>?

    fun useProtocol(proto: MinecraftProtocol)

    /**
     * Disconnects the client, blocking the current thread.
     */
    fun disconnect(reason: String?, cause: Throwable? = null)

    /**
     * Deactivate and remove any old behavior, and
     * call [Behavior.activate].
     */
    fun useBehavior(behavior: Behavior)

    /**
     * Indicates if the account is logged into the server at this time.
     *
     * depends on endReason because connection remains
     * set after disconnection, for info/debugging purposes
     */
    val connected get() = connection != null && endReason == null

    /**
     * Indicates if the account has received all its state yet, such as
     * position, health/food/exp, and is also still [connected].
     */
    val spawned: Boolean
        get() = (position != null
            && health != null
            && experience != null
            && world != null
            && connected)

    /**
     * Indicates if the account is alive at this time, including being [spawned].
     */
    val alive get() = health ?: 0.0f > 0.0f && spawned
}
