package com.github.gjum.minecraft.botlin.api

import com.github.steveice10.mc.auth.data.GameProfile
import com.github.steveice10.mc.protocol.MinecraftProtocol
import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode
import com.github.steveice10.packetlib.Session
import java.util.UUID

suspend fun ServiceRegistry.getAvatar() = consumeService(Avatar::class.java)!!

/**
 * State (embodiment) of an account on one server,
 * uniquely identified by [profile] and [serverAddress].
 */
interface Avatar : EventEmitter<AvatarEvent>, Service {
	val serviceRegistry: ServiceRegistry // XXX really does not belong here

	val identifier get() = "${profile.name}@$serverAddress"

	val profile: GameProfile

	/** Normalized to format "host:port". */
	val serverAddress: String

	val connection: Session?
	val endReason: String?
	val entity: Entity?
	val health: Float?
	val food: Int?
	val saturation: Float?
	val experience: Experience?
	var position: Vec3d?
	var look: Look?
	val onGround: Boolean?
	val gameMode: GameMode?
	val inventory: Window?
	val world: World?
	val playerList: Map<UUID, PlayerListItem>?

	/**
	 * Starts connecting to [serverAddress] as [profile].
	 * Does not wait for connection to succeed.
	 */
	fun useProtocol(proto: MinecraftProtocol) // XXX rename

	/**
	 * Disconnects the client, blocking the current thread.
	 */
	fun disconnect(reason: String?, cause: Throwable? = null)

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
	val spawned: Boolean // TODO rename to 'ingame'?
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
