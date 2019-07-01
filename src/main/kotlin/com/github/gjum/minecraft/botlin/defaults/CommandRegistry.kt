package com.github.gjum.minecraft.botlin.defaults

import com.github.gjum.minecraft.botlin.api.Command
import com.github.gjum.minecraft.botlin.api.CommandContext
import com.github.gjum.minecraft.botlin.api.CommandService
import com.github.gjum.minecraft.botlin.api.registerCommand
import java.util.logging.Logger

class CommandRegistry : CommandService {
	private val logger = Logger.getLogger(this::class.java.name)

	private val commands = mutableMapOf<String, Command>()

	init {
		registerCommand("help [command]", "Show help for that command, or list all commands."
		) { cmdLine, context ->
			if (cmdLine.trim() == "help") {
				context.respond("Available commands:\n"
					+ (commands.values
					.sortedBy { it.name }
					.joinToString("\n") {
						"${it.usage} - ${it.description}"
					}))
			} else {
				val cmdName = cmdLine.split(" +".toRegex())[1]
				val cmd = commands[cmdName]
				if (cmd != null) {
					context.respond("${cmd.usage} - ${cmd.description}")
				} else {
					context.respond("Unknown command `$cmdName` - try just `help` for a list of commands.")
				}
			}
		}
	}

	override fun registerCommand(command: Command): Boolean {
		val alreadyRegisteredCmd = commands.putIfAbsent(command.name, command)
		if (alreadyRegisteredCmd != null) {
			logger.warning("Command ${command.name} already registered, ignoring")
			return false
		} else {
			logger.fine("Registered command ${command.usage} - ${command.description}")
			return true
		}
	}

	override fun executeCommand(commandLine: String, context: CommandContext): Boolean {
		if (commandLine.trim().isEmpty()) return false
		val isSlashCommand = commandLine.getOrNull(0)?.equals('/') == true
		val cmdLineAdjusted = if (isSlashCommand) "say $commandLine" else commandLine
		val cmdName = cmdLineAdjusted.split(" +".toRegex())[0]
		val command = commands[cmdName]
		command?.handle(cmdLineAdjusted, context)
		return command != null
	}
}
