package com.github.gjum.minecraft.botlin

import com.github.gjum.minecraft.botlin.modules.defaults.AvatarProvider
import com.github.gjum.minecraft.botlin.modules.defaults.EventLoggerModule
import com.github.gjum.minecraft.botlin.util.Cli
import com.github.gjum.minecraft.botlin.util.Reauth.reauth
import com.github.gjum.minecraft.botlin.util.toAnsi
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientChatPacket
import com.github.steveice10.packetlib.Client
import com.github.steveice10.packetlib.tcp.TcpSessionFactory
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

object Main {
    private val logger = Logger.getLogger(this::class.java.name)

    @JvmStatic
    fun main(args: Array<String>) {
        val username = args.getOrNull(0) ?: System.getenv("MINECRAFT_USERNAME") ?: "Botlin"
        val password = System.getenv("MINECRAFT_PASSWORD") ?: ""
        val host = args.getOrNull(1)
        startCli(username, password, host ?: "localhost")
    }

    fun startCli(
        username: String, password: String = "",
        host: String = "localhost", port: Int = 25565
    ) {
        var aToken = ""
        val cToken = UUID.randomUUID().toString()
        val avatarProvider = AvatarProvider(mutableMapOf())
        val bot = runBlocking { avatarProvider.getAvatar(username, "$host:$port") }

        EventLoggerModule().registerWithAvatar(bot) // XXX

        try {
            run {
                val proto = reauth(username, password, aToken, cToken)
                aToken = proto.accessToken ?: ""
                val client = Client(host, port, proto, TcpSessionFactory())
                bot.useConnection(client.session)
                client.session.connect(false)
            }

            Cli.run { cmdLine ->
                val isSlashCommand = cmdLine.getOrNull(0)?.equals('/') == true
                val cmdLineAdjusted = if (isSlashCommand) "say $cmdLine" else cmdLine
                val split = cmdLineAdjusted.split(" +".toRegex())
                when (val command = split.first()) {
                    "" -> Unit
                    "exit" -> Cli.stop()
                    "help" -> {
                        logger.info(
                            """
                            Available commands:
                            connect - Connect to server.
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
                        logger.info("Authenticating as $username")
                        val proto = reauth(username, password, aToken, cToken)
                        aToken = proto.accessToken ?: ""
                        logger.info("Connecting as ${proto.profile.name} auth=${aToken != ""}")
                        val client = Client(host, port, proto, TcpSessionFactory())
                        bot.useConnection(client.session)
                        client.session.connect(false)
                    }
                    "logout" -> GlobalScope.launch { bot.disconnect("CLI disconnect command") }
                    "info" -> {
                        bot.apply {
                            logger.info("${profile?.name} at $position $look on ${connection?.remoteAddress}")
                            logger.info("health=$health food=$food sat=$saturation exp=${experience?.total} (${experience?.level} lvl)")
                        }
                    }
                    "list" -> {
                        val namesSpaceSep = bot.playerList!!.values.joinToString(" ") {
                            it.displayName?.toAnsi() ?: it.profile.name
                        }
                        logger.info("Connected players: $namesSpaceSep")
                    }
                    "say" -> {
                        val msg = cmdLineAdjusted.substring(command.length + 1)
                        bot.connection!!.send(ClientChatPacket(msg))
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
}
