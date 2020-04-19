package com.github.gjum.minecraft.botlin.impl

import com.github.gjum.minecraft.botlin.api.AvatarEvent
import com.github.gjum.minecraft.botlin.api.ClientConnection
import com.github.gjum.minecraft.botlin.api.EventBoard
import com.github.gjum.minecraft.botlin.api.postAsync
import com.github.gjum.minecraft.botlin.util.AuthenticationProvider
import com.github.gjum.minecraft.botlin.util.normalizeServerAddress
import com.github.gjum.minecraft.botlin.util.splitHostPort
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientPluginMessagePacket
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerJoinGamePacket
import com.github.steveice10.packetlib.Session
import com.github.steveice10.packetlib.event.session.*
import com.github.steveice10.packetlib.packet.Packet
import com.github.steveice10.packetlib.tcp.TcpSessionFactory
import java.util.logging.Level
import java.util.logging.Logger

private val brandBytesVanilla = byteArrayOf(7, 118, 97, 110, 105, 108, 108, 97)

class ClientConnectionImpl(
	private val eventBoard: EventBoard<AvatarEvent>,
	private val avatar: MutableAvatar,
	private val auth: AuthenticationProvider
) : ClientConnection, SessionListener {
	private val logger = Logger.getLogger("ClientConnection")

	private var connection: Session? = null
	override var serverAddress: String? = null
	override var endReason: String? = null

	override val connected get() = connection?.isConnected ?: false

	override suspend fun connect(serverAddress: String, waitForSpawn: Boolean) {
		val address = normalizeServerAddress(serverAddress)
		if (connected) {
			if (address == this.serverAddress) logger.warning("Not connecting to $serverAddress, already connected to ${connection!!.remoteAddress}")
			else disconnect("Connecting to $serverAddress")
		}
		val (host, port) = splitHostPort(address)
		logger.fine("Connecting as ${avatar.profile.name} to $host $port")
		val (proto, authError) = auth.authenticate()
		if (proto == null) throw IllegalStateException("Failed to authenticate: $authError", authError)
		val client = com.github.steveice10.packetlib.Client(host, port, proto, TcpSessionFactory())
		this.serverAddress = address
		connection = client.session
		avatar.reset()
		client.session.addListener(this)
		// XXX suspend until connection success/failure event; if error, throw
		client.session.connect(/* wait = */true)
		if (waitForSpawn) eventBoard.receiveSingle(AvatarEvent.Spawned::class.java)
	}

	override fun disconnect(reason: String?, cause: Throwable?) {
		val c = connection ?: return
		synchronized(avatar) {
			if (endReason == null) {
				eventBoard.postAsync(AvatarEvent.Disconnected(c, reason, cause))
			}
			// avatar.reset() // remember the fail state
			endReason = reason ?: "Intentionally disconnected"
		}
		// TODO submit upstream patch for TcpClientSession overriding all TcpSession#disconnect
		connection?.disconnect(reason, cause, /* wait = */false)
		connection = null
		serverAddress = null
	}

	override fun sendPacket(packet: Packet) {
		connection?.send(packet)
			?: logger.warning("Tried sending packet while not connected: $packet")
	}

	override fun connected(event: ConnectedEvent) {
		eventBoard.postAsync(AvatarEvent.Connected(event.session))
	}

	override fun disconnecting(event: DisconnectingEvent) {
		event.apply { disconnect(reason, cause) }
	}

	override fun disconnected(event: DisconnectedEvent) {
		event.apply { disconnect(reason, cause) }
	}

	override fun packetSending(event: PacketSendingEvent) = Unit

	override fun packetSent(event: PacketSentEvent) = Unit

	override fun packetReceived(event: PacketReceivedEvent) {
		val packet = event.getPacket<Packet>()
		try {
			if (packet is ServerJoinGamePacket) {
				sendPacket(ClientPluginMessagePacket("MC|Brand", brandBytesVanilla))
				sendPacket(avatar.settings)
			}
			avatar.handleServerPacket(packet)
		} catch (e: Throwable) { // and rethrow
			Logger.getLogger(this.javaClass.name).log(Level.SEVERE, "Failed to process received packet $packet", e)
			throw e
		}
		eventBoard.postAsync(AvatarEvent.ServerPacketReceived(packet))
	}
}
