package com.github.gjum.minecraft.botlin

import com.github.gjum.minecraft.botlin.Reauth.reauth
import com.github.steveice10.mc.protocol.MinecraftProtocol
import com.github.steveice10.mc.protocol.data.message.Message
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientChatPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.window.ServerWindowPropertyPacket
import com.github.steveice10.packetlib.Client
import com.github.steveice10.packetlib.Session
import com.github.steveice10.packetlib.tcp.TcpSessionFactory
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

object Main {
    private val logger: Logger = Logger.getLogger(this::class.java.name)

    @JvmStatic
    fun main(args: Array<String>) {
        val username = args.getOrNull(0) ?: System.getenv("MINECRAFT_USERNAME") ?: "Botlin"
        val password = System.getenv("MINECRAFT_PASSWORD") ?: ""
        val host = args.getOrNull(1)
        startCli(username, password, host)
    }

    fun startCli(
        username: String, password: String = "",
        host: String? = null, port: Int = 25565
    ) {
        var aToken = ""
        val cToken = UUID.randomUUID().toString()
        val bot = McBot()
        bot.registerListeners(ChatLogger())
        bot.registerListeners(MiscEventLogger())
        bot.registerListeners(ReadyStateLogger(bot))

        try {
            host?.let {
                val proto = reauth(username, password, aToken, cToken)
                aToken = proto.accessToken ?: ""
                val client = Client(host, port, proto, TcpSessionFactory())
                bot.useConnection(client.session, proto.profile)
                client.session.connect(false)
            }

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
                            it.displayName?.toAnsi() ?: it.profile.name
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

class ChatLogger : IChatListener {
    private val logger: Logger = Logger.getLogger(this::class.java.name)
    override fun onChatReceived(msg: Message) = logger.info("[CHAT] ${msg.toAnsi()}")
}

class MiscEventLogger : IInventoryListener, IPlayerListListener, IPlayerStateListener {
    private val logger: Logger = Logger.getLogger(this::class.java.name)

    override fun onWindowReady(window: McWindow) = window.run {
        logger.info("Window ready: $windowTitle with $slotCount slots")
    }

    override fun onWindowClosed() = logger.info("Window closed")

    override fun onWindowPropertyChanged(property: ServerWindowPropertyPacket) = property.run {
        logger.info("Window property changed: $rawProperty = $value")
    }

    override fun onSlotsChanged() = logger.info("Slots changed")

    override fun onPlayerJoined(entry: PlayerListItem) = logger.info(
        "Player joined: ${entry.displayName?.toAnsi() ?: entry.profile.name}"
    )

    override fun onPlayerLeft(entry: PlayerListItem) = logger.info(
        "Player left: ${entry.displayName?.toAnsi() ?: entry.profile.name}"
    )

    override fun onPositionChanged(position: Vec3d) = logger.info(
        "Position changed to $position"
    )

    override fun onPlayerEntityStatusChanged() = logger.info(
        "Player entity status changed"
    )
}

class ReadyStateLogger(private val bot: IBot) : IReadyListener {
    private val logger: Logger = Logger.getLogger(this::class.java.name)

    override fun onConnected(connection: Session) = logger.info("Connected to ${connection.remoteAddress}")

    override fun onSpawned() = logger.info(
        "Spawned at ${bot.position} ${bot.look} as eid ${bot.entity?.eid}"
    )

    override fun onDisconnected(reason: String?, cause: Throwable?) {
        if (cause != null) logger.log(Level.FINE, cause.toString(), cause)
        logger.warning("Disconnected from ${bot.remoteAddress} Reason: $reason")
    }
}
