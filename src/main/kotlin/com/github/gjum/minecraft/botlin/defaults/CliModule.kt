package com.github.gjum.minecraft.botlin.defaults

import com.github.gjum.minecraft.botlin.api.*
import com.github.gjum.minecraft.botlin.modules.ReloadableServiceRegistry
import com.github.gjum.minecraft.botlin.util.Cli
import com.github.gjum.minecraft.botlin.util.runOnThread
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
		serviceRegistry.launch {
			try {
				runOnThread {
					Cli.run {
						handleCommand(it, commands)
					}
				}
			} catch (e: Throwable) { // and rethrow
				commandLogger.log(Level.SEVERE, "Error in CLI: $e", e)
				throw e
			} finally {
				Cli.stop()
				// XXX this shutdown stuff might throw more errors, but shouldn't
				// TODO emit some endRequested event
				(serviceRegistry as ReloadableServiceRegistry).teardown()
			}
		}
	}

	override fun deactivate() {
		super.deactivate()
		Cli.stop()
	}
}

private fun handleCommand(cmdLineRaw: String, commands: CommandService) {
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
