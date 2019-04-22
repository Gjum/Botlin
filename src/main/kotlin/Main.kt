package com.github.gjum.minecraft.botlin

import com.github.gjum.minecraft.botlin.Log.logger
import com.github.gjum.minecraft.botlin.Reauth.reauth
import com.github.steveice10.mc.protocol.MinecraftProtocol
import com.github.steveice10.packetlib.Client
import com.github.steveice10.packetlib.tcp.TcpSessionFactory
import java.util.*
import java.util.logging.Level

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
        bot.disconnect("End of Main")
    }

    /**
     * Wrapper around connect(reauth(), host, port, ...) with additional logging
     */
    fun connect(
        host: String = "localhost", port: Int = 25565,
        username: String = "Botlin", password: String = "",
        waitForConnectionToEstablish: Boolean = true
    ): IBot {
        logger.info("Authenticating as $username")
        val proto = reauth(username, password)
        val authMsg = if (proto.accessToken ?: "" != "") "authenticated" else "unauthenticated"
        logger.info("Connecting as ${proto.profile.name} ($authMsg)")
        val bot = connect(proto, host, port, waitForConnectionToEstablish)
        logger.info("Connected to ${bot.remoteAddress}")
        return bot
    }

    fun connect(
        proto: MinecraftProtocol,
        host: String = "localhost", port: Int = 25565,
        waitForConnectionToEstablish: Boolean = true
    ): IBot {
        val client = Client(host, port, proto, TcpSessionFactory())
        val bot = McBot().useConnection(client.session, proto.profile)
        client.session.connect(waitForConnectionToEstablish)
        return bot
    }
}
