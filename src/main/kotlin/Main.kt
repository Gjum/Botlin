package com.github.gjum.minecraft.botlin

import com.github.gjum.minecraft.botlin.Log.logger
import com.github.gjum.minecraft.botlin.Reauth.reauth
import com.github.steveice10.packetlib.Client
import com.github.steveice10.packetlib.tcp.TcpSessionFactory

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        connect()
    }

    fun connect(
        host: String = "localhost", port: Int = 25565,
        username: String = "Botlin", password: String = "",
        waitConnectionToFinish: Boolean = false
    ) {
        logger.info("Authenticating")
        val authResult = reauth(username, password)
        val proto = authResult.protocol
        if (proto == null) {
            logger.info("Error during authentication: ${authResult.error?.message}")
            return
        }
        val authMsg = if (proto.accessToken != "") "authenticated" else "unauthenticated"
        logger.info("Playing as ${proto.profile.name} $authMsg")

        val client = Client(host, port, proto, TcpSessionFactory())
        val bot = McBot().useConnection(client.session, proto.profile)
        logger.info("Connecting")
        client.session.connect(waitConnectionToFinish)
        // TODO why does main not exit after disconnect?
    }
}
