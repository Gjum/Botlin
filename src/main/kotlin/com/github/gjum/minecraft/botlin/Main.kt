package com.github.gjum.minecraft.botlin

import com.github.gjum.minecraft.botlin.behaviors.EventLogger
import com.github.gjum.minecraft.botlin.cli.*
import com.github.gjum.minecraft.botlin.impl.setupBot
import com.github.gjum.minecraft.botlin.util.runOnThread
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

		val parentScope = this
		val bot = setupBot(username, listOf(::EventLogger))

		val commandRegistry = CommandRegistryImpl()
		registerUsefulCommands(commandRegistry, bot, parentScope)

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
				bot.disconnect("Closing the CLI")
				parentScope.coroutineContext.cancel() // TODO emit some endRequested event
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
	} catch (e: Throwable) {
		context.respond("Error: $e")
		commandLogger.log(Level.WARNING, "Error while running command '$cmdName'", e)
	}
}

private val commandLogger = Logger.getLogger("com.github.gjum.minecraft.botlin.Commands")

private class LoggingCommandContext(val cmdName: String) : CommandContext {
	override fun respond(message: String) {
		commandLogger.info("[$cmdName] $message")
	}
}
