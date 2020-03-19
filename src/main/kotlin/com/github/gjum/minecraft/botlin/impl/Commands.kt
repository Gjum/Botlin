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
	val aliases: List<String>
	val usage: String
	val description: String
	fun handle(commandLine: String, context: CommandContext)
}

/**
 * [Command] creation helper that accepts the required info
 * as params instead of overriding fields.
 */
abstract class CommandHelper(
	override val name: String,
	override val aliases: List<String> = emptyList(),
	override val usage: String,
	override val description: String
) : Command

/**
 * Service specification of command registration and execution.
 */
interface CommandRegistry {
	/**
	 * Register [command] under its [Command.name] and [Command.aliases].
	 * Skips if another command with the same name is already registered.
	 * @return true if the [command] was registered under any name.
	 */
	fun registerCommand(command: Command): Boolean

	/**
	 * Execute the given [commandLine] with the given [context].
	 * @return false if no registered command matches the [commandLine].
	 */
	fun executeCommand(commandLine: String, context: CommandContext): Boolean
}

/**
 * [Command] creation helper that accepts the required info and handler
 * as params instead of overriding fields.
 */
fun CommandRegistry.registerCommand(
	usage: String,
	description: String,
	aliases: List<String> = emptyList(),
	block: Command.(String, CommandContext) -> Unit
): Boolean {
	val cmdName = usage.substringBefore(' ')
	return registerCommand(object : CommandHelper(cmdName, aliases, usage, description) {
		override fun handle(commandLine: String, context: CommandContext) {
			block(commandLine, context)
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
					.distinct()
					.sortedBy { it.name }
					.joinToString("\n") {
						val aliases = if (it.aliases.isEmpty()) ""
						else " Aliases: " + it.aliases.joinToString(", ")
						"${it.usage} - ${it.description}$aliases"
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
		var registeredAny = false
		for (name in command.aliases.plus(command.name)) {
			val alreadyRegisteredCmd = commands.putIfAbsent(name, command)
			if (alreadyRegisteredCmd == null) {
				val usage = name + ' ' + command.usage.substringAfter(' ')
				logger.fine("Registered command: $usage - ${command.description}")
				registeredAny = true
			} else {
				logger.warning("Command '$name' already registered, ignoring")
			}
		}
		return registeredAny
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
