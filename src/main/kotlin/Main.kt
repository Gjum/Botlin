package com.github.gjum.minecraft.botlin

import com.github.gjum.minecraft.botlin.Log.logger
import com.github.gjum.minecraft.botlin.Reauth.reauth
import com.github.steveice10.mc.protocol.MinecraftProtocol
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientChatPacket
import com.github.steveice10.packetlib.Client
import com.github.steveice10.packetlib.tcp.TcpSessionFactory
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*
import java.util.logging.Level

object Main {
    @JvmStatic
    fun main(args: Array<String>) {
        val username = System.getenv("MINECRAFT_USERNAME") ?: "Botlin"
        val password = System.getenv("MINECRAFT_PASSWORD") ?: ""
        start(username, password)
    }

    fun start(username: String, password: String = "") {
        var aToken = ""
        val cToken = UUID.randomUUID().toString()
        val bot = McBot()
        try {
            Cli.start { cmdLine ->
                val split = cmdLine.split(" +".toRegex())
                val command = split.first()
                when (command) {
                    "" -> Unit
                    "exit" -> Cli.stop()
                    "help" -> {
                        logger.info(
                            """
                            Available commands:
                            connect [host=localhost] [port=25565] - Connect to server.
                            logout - Disconnect from server.
                            exit - Exit the command line and disconnect the bot.
                            info - Show bot info: connection, location, health, etc.
                            list - Show connected players list.
                            say - Send a chat message to the server.
                            """.trimIndent()
                        )
                    }
                    "connect" -> {
                        // TODO bail if already connected, before authenticating - Mojang doesn't allow multiple sessions with same token
                        val host = split.getOrElse(1) { "localhost" }
                        val port = split.getOrElse(2) { "25565" }.toInt()
                        logger.info("Authenticating as $username")
                        val proto = reauth(username, password, aToken, cToken)
                        aToken = proto.accessToken ?: ""
                        logger.info("Connecting as ${proto.profile.name} auth=${aToken != ""}")
                        val client = Client(host, port, proto, TcpSessionFactory())
                        bot.useConnection(client.session, proto.profile)
                        client.session.connect(false)
                    }
                    "logout" -> GlobalScope.launch { bot.disconnect("CLI disconnect command") }
                    "info" -> {
                        bot.apply {
                            logger.info("${profile?.name} at $position $look on $remoteAddress")
                            logger.info("health=$health food=$food sat=$saturation exp=${experience?.total} (${experience?.level} lvl)")
                        }
                    }
                    "list" -> {
                        val namesSpaceSep = bot.playerList.values.joinToString(" ") {
                            it.displayName?.fullText ?: it.profile.name
                        }
                        logger.info("Connected players: $namesSpaceSep")
                    }
                    "say" -> {
                        val msg = cmdLine.substring(command.length + 1)
                        bot.send(ClientChatPacket(msg))
                    }
                    else -> logger.info("Unknown command: $command")
                }
            }
        } catch (e: Throwable) {
            logger.log(Level.SEVERE, "Error in CLI: $e", e)
        } finally {
            Cli.stop()
            bot.disconnect("End of Main")
        }
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
