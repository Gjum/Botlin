package com.github.gjum.minecraft.botlin.api

/**
 * Passed to command handler during execution.
 */
interface CommandContext {
    fun respond(message: String)
}

/**
 * A command that can be registered to a [CommandService].
 */
interface Command {
    val prefix: String
    val usage: String
    val description: String
    fun handle(command: String, context: CommandContext)
}

/**
 * [Command] creation helper that accepts the required info
 * as params instead of overriding fields.
 */
abstract class CommandHelper(
    override val prefix: String,
    override val usage: String,
    override val description: String
) : Command

/**
 * [Command] creation helper that accepts the required info and handler
 * as params instead of overriding fields.
 */
fun CommandService.registerCommand(
    prefix: String,
    usage: String,
    description: String,
    block: (String, CommandContext) -> Unit
) = registerCommand(object : CommandHelper(prefix, usage, description) {
    override fun handle(command: String, context: CommandContext) {
        block(command, context)
    }
})

/**
 * Service specification of command registration and execution.
 */
interface CommandService : Service {
    /**
     * Register the given [command] implemented by the given [module].
     * Returns false if a command with the same prefix is already registered,
     * in which case this [command] is ignored.
     */
    fun registerCommand(command: Command): Boolean

    /**
     * Execute the given [commandLine] with the given [context].
     * Returns false if no registered command matches the [commandLine].
     */
    fun executeCommand(commandLine: String, context: CommandContext): Boolean
}
