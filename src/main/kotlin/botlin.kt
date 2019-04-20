package com.github.gjum.minecraft.botlin

import com.github.gjum.minecraft.botlin.Log.log
import com.github.gjum.minecraft.botlin.Reauth.reauth
import com.github.steveice10.mc.protocol.data.message.Message
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerChatPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerDisconnectPacket
import com.github.steveice10.packetlib.Client
import com.github.steveice10.packetlib.Session
import com.github.steveice10.packetlib.event.session.*
import com.github.steveice10.packetlib.packet.Packet
import com.github.steveice10.packetlib.tcp.TcpSessionFactory
import java.util.logging.Level

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        connect()
    }

    fun connect(
        host: String = "localhost", port: Int = 25565,
        username: String = "Botlin", password: String = ""
    ) {
        log("Authenticating")
        val authResult = reauth(username, password)
        val proto = authResult.protocol
        if (proto == null) {
            log("Error during authentication: ${authResult.error?.message}")
            return
        }
        val authMsg = if (proto.accessToken != "") "authenticated" else "unauthenticated"
        log("Playing as ${proto.profile.name} $authMsg")

        val client = Client(host, port, proto, TcpSessionFactory())
        val bot = Bot()
        bot.useConnection(client.session)
        log("Connecting")
        client.session.connect() // XXX pass true?
    }
}

class Bot : SessionListener {
    fun useConnection(connection: Session) {
        connection.addListener(this)
    }

    override fun connected(event: ConnectedEvent) {
        log("Connected to ${event.session.remoteAddress}")
    }

    override fun disconnecting(event: DisconnectingEvent) {
        event.session.disconnect(event.reason)
    }

    override fun disconnected(event: DisconnectedEvent) {
        log(event.cause.stackTrace.joinToString("\n", transform = StackTraceElement::toString), Level.FINE)
        val reason = Message.fromString(event.reason).fullText
        event.session.run { log("Disconnected from $host:$port Reason: $reason") }
    }

    override fun packetReceived(event: PacketReceivedEvent) {
        val p = event.getPacket<Packet>()
        when (p) {
            is ServerChatPacket -> log("[CHAT] ${p.message.fullText}")
            is ServerDisconnectPacket -> event.session.disconnect(p.reason.fullText)
            else -> log("Got packet: ${p::class.qualifiedName}")
        }
        // TODO handle packets
    }

    override fun packetSending(p0: PacketSendingEvent?) {
    }

    override fun packetSent(p0: PacketSentEvent?) {
    }
}
