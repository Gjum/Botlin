package com.github.gjum.minecraft.botlin.api

import com.github.steveice10.packetlib.packet.Packet

class Kicked(message: String) : RuntimeException(message)

interface ClientConnection {
	/**
	 * Normalized to format "host:port".
	 * Null if this was never connected to a server.
	 */
	val serverAddress: String?

	val connected: Boolean

	/**
	 * Kick/disconnect message, or null if still online.
	 */
	val endReason: String?

	/**
	 * Connects to the [serverAddress].
	 * Waits until [AvatarEvent.Spawned], unless when [waitForSpawn] is false,
	 * in which case this only waits until the connection succeeded/failed.
	 * Throws an error if the connection failed.
	 */
	suspend fun connect(serverAddress: String, waitForSpawn: Boolean = true)

	fun disconnect(reason: String, cause: Throwable? = null)

	fun sendPacket(packet: Packet)
}
