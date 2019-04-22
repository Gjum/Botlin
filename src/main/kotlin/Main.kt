package com.github.gjum.minecraft.botlin

import com.github.gjum.minecraft.botlin.Log.logger
import com.github.gjum.minecraft.botlin.Reauth.reauth
import com.github.steveice10.packetlib.Client
import com.github.steveice10.packetlib.tcp.TcpSessionFactory

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val bot = connect()
        Cli.start { command ->
            when (command) {
                "list" -> {
                    val namesSpaceSep = bot.playerList.values.joinToString(" ") {
                        it.displayName?.fullText ?: it.profile.name
                    }
                    logger.info("Connected players: $namesSpaceSep")
                }
                "exit" -> Cli.stop()
                else -> logger.info("Unknown command: $command")
            }
        }
    }

    fun connect(
        host: String = "localhost", port: Int = 25565,
        username: String = "Botlin", password: String = "",
        waitForConnectionToEstablish: Boolean = true
    ): McBot {
        logger.info("Authenticating")
        val proto = reauth(username, password)
        val authMsg = if (proto.accessToken != "") "authenticated" else "unauthenticated"
        logger.info("Playing as ${proto.profile.name} $authMsg")

        val client = Client(host, port, proto, TcpSessionFactory())
        val bot = McBot().useConnection(client.session, proto.profile)
        logger.info("Connecting")
        client.session.connect(waitForConnectionToEstablish)
        return bot
    }
}
