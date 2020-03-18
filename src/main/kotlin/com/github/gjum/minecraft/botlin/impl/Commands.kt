package com.github.gjum.minecraft.botlin.impl

import java.util.logging.Logger

/**
 * Passed to command handler during execution.
 */
interface CommandContext {
	fun respond(message: String)
}

/**
 * A command that can be registered to a [CommandRegistry].
 */
interface Command {
	val name: String
	val usage: String
	val description: String
	fun handle(commandLine: String, context: CommandContext)
}

/**
 * Service specification of command registration and execution.
 */
interface CommandRegistry {
	/**
	 * Register the given [command].
	 * Returns false if a command with the same name is already registered,
	 * in which case this [command] is ignored.
	 */
	fun registerCommand(command: Command): Boolean

	/**
	 * Execute the given [commandLine] with the given [context].
	 * Returns false if no registered command matches the [commandLine].
	 */
	fun executeCommand(commandLine: String, context: CommandContext): Boolean
}

/**
 * [Command] creation helper that accepts the required info
 * as params instead of overriding fields.
 */
abstract class CommandHelper(
	override val name: String,
	override val usage: String,
	override val description: String
) : Command

/**
 * [Command] creation helper that accepts the required info and handler
 * as params instead of overriding fields.
 */
fun CommandRegistry.registerCommand(
	usage: String,
	description: String,
	block: (String, CommandContext) -> Unit
): Boolean {
	val cmdName = usage.substringBefore(' ')
	return registerCommand(object : CommandHelper(cmdName, usage, description) {
		override fun handle(command: String, context: CommandContext) {
			block(command, context)
		}
	})
}

class CommandRegistryImpl : CommandRegistry {
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
					context.respond("Unknown command '$cmdName' - try just 'help' for a list of commands.")
				}
			}
		}
	}

	override fun registerCommand(command: Command): Boolean {
		val alreadyRegisteredCmd = commands.putIfAbsent(command.name, command)
		return if (alreadyRegisteredCmd == null) {
			logger.fine("Registered command: ${command.usage} - ${command.description}")
			true
		} else {
			logger.warning("Command '${command.name}' already registered, ignoring")
			false
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
