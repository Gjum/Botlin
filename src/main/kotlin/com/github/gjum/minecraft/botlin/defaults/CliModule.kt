package com.github.gjum.minecraft.botlin.defaults

import com.github.gjum.minecraft.botlin.api.CommandContext
import com.github.gjum.minecraft.botlin.api.CommandService
import com.github.gjum.minecraft.botlin.api.Module
import com.github.gjum.minecraft.botlin.api.ServiceRegistry
import com.github.gjum.minecraft.botlin.modules.consumeService
import com.github.gjum.minecraft.botlin.util.Cli
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Wraps stdin/stdout/stderr to set up a command line interface.
 */
class CliModule : Module() {
	override suspend fun activate(serviceRegistry: ServiceRegistry) {
		val commands = serviceRegistry.consumeService(CommandService::class.java)
			?: return // no commands, no cli

		// non-blocking
		GlobalScope.launch {
			try {
				Cli.run { cmdLineRaw ->
					if (cmdLineRaw.trim().isEmpty()) return@run
					val isSlashCommand = cmdLineRaw.getOrNull(0)?.equals('/') == true
					val cmdLine = if (isSlashCommand) "say $cmdLineRaw" else cmdLineRaw
					val cmdName = cmdLine.split(' ')[0]
					val context = LoggingCommandContext(cmdName)
					try {
						if (!commands.executeCommand(cmdLine, context)) {
							commandLogger.warning("Unknown command: $cmdLine")
						}
					} catch (e: Exception) {
						context.respond("Error while running command: $e")
						commandLogger.log(Level.WARNING, "Error while running command $cmdName", e)
					}
				}
			} catch (e: Throwable) {
				commandLogger.log(Level.SEVERE, "Error in CLI: $e", e)
				e.printStackTrace()
			} finally {
				Cli.stop()
			}
			// TODO emit some endRequested event
		}
	}

	override fun deactivate() {
		super.deactivate()
		Cli.stop()
	}
}

private val commandLogger = Logger.getLogger("com.github.gjum.minecraft.botlin.Commands")

private class LoggingCommandContext(val cmdName: String) : CommandContext {
	override fun respond(message: String) {
		commandLogger.info("[$cmdName] $message")
	}
}
