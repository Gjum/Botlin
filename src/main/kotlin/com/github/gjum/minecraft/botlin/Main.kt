package com.github.gjum.minecraft.botlin

import com.github.gjum.minecraft.botlin.api.Bot
import com.github.gjum.minecraft.botlin.impl.*
import com.github.gjum.minecraft.botlin.util.Cli
import com.github.gjum.minecraft.botlin.util.runOnThread
import com.github.gjum.minecraft.botlin.util.toAnsi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.logging.Level
import java.util.logging.Logger

object Main {
	@JvmStatic
	fun main(args: Array<String>) = runBlocking {
		val username = args.getOrNull(0) ?: "Botlin"
		val address = args.getOrNull(1)

		val bot = setupBot(username, listOf(::EventLogger))

		val commandRegistry = CommandRegistryImpl()
		registerUsefulCommands(commandRegistry, bot, this)

		launch {
			try {
				runOnThread {
					Cli.run {
						handleCommand(it, commandRegistry)
					}
				}
			} catch (e: Throwable) { // and rethrow
				commandLogger.log(Level.SEVERE, "Error in CLI: $e", e)
				throw e
			} finally {
				Cli.stop()
				// TODO emit some endRequested event
			}
		}

		if (address != null) bot.connect(address)
	}
}

private fun handleCommand(cmdLineRaw: String, commands: CommandRegistry) {
	val cmdLineClean = cmdLineRaw.trim()
	if (cmdLineClean.isEmpty()) return
	val isSlashCommand = cmdLineClean.getOrNull(0)?.equals('/') == true
	val cmdLine = if (isSlashCommand) "say $cmdLineClean" else cmdLineClean
	val cmdName = cmdLine.substringBefore(' ')
	val context = LoggingCommandContext(cmdName)
	try {
		if (!commands.executeCommand(cmdLine, context)) {
			commandLogger.warning("Unknown command: $cmdLine")
		}
	} catch (e: Throwable) { // and rethrow
		context.respond("Error while running command: $e")
		commandLogger.log(Level.WARNING, "Error while running command '$cmdName'", e)
		throw e
	}
}

private val commandLogger = Logger.getLogger("com.github.gjum.minecraft.botlin.Commands")

private class LoggingCommandContext(val cmdName: String) : CommandContext {
	override fun respond(message: String) {
		commandLogger.info("[$cmdName] $message")
	}
}

fun registerUsefulCommands(commands: CommandRegistry, bot: Bot, parentScope: CoroutineScope) {
	commands.registerCommand("quit", "Close the program."
	) { _, _ ->
		bot.disconnect("Closing the program")
		parentScope.coroutineContext.cancel()
	}
	commands.registerCommand("connect <address>", "Connect to server."
	) { cmdLine, _ ->
		// TODO bail if already connected, before authenticating - Mojang doesn't allow multiple sessions with same token
		parentScope.launch { bot.connect(cmdLine.split(' ')[1]) }
	}
	commands.registerCommand("disconnect", "Disconnect from server."
	) { _, _ ->
		bot.disconnect("CLI disconnect command")
	}
	commands.registerCommand("info", "Show bot info: connection, location, health, etc."
	) { _, context ->
		bot.playerEntity?.run {
			context.respond("${bot.profile.name} at $position $look on ${bot.serverAddress}")
			context.respond("health=$health food=$food sat=$saturation exp=${experience?.total} (${experience?.level} lvl)")
		} ?: context.respond("${bot.profile.name} (not spawned)")
	}
	commands.registerCommand("list", "Show connected players list."
	) { _, context ->
		val namesSpaceSep = bot.playerList.values
			.sortedBy { it.profile.name }
			.joinToString(" ") {
				it.displayName?.toAnsi() ?: it.profile.name
			}
		context.respond("Connected players: $namesSpaceSep")
	}
	commands.registerCommand("say <message>", "Send a chat message to the server."
	) { cmdLine, context ->
		val msg = cmdLine.substring("say ".length)
		if (bot.alive) parentScope.launch { bot.chat(msg) }
		else if (bot.connected) context.respond("Can't chat while dead")
		else context.respond("Not connected to any server")
	}
}
