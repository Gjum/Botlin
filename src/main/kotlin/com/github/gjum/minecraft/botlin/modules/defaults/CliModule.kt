package com.github.gjum.minecraft.botlin.modules.defaults

import com.github.gjum.minecraft.botlin.api.CommandContext
import com.github.gjum.minecraft.botlin.api.CommandService
import com.github.gjum.minecraft.botlin.api.Module
import com.github.gjum.minecraft.botlin.api.registerCommand
import com.github.gjum.minecraft.botlin.modules.ReloadableServiceRegistry
import com.github.gjum.minecraft.botlin.util.Cli
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Wraps stdin/stdout/stderr to set up a command line interface.
 */
class CliModule : Module() {
    private val logger = Logger.getLogger(this::class.java.name)

    override suspend fun initialize(serviceRegistry: ReloadableServiceRegistry, oldModule: Module?) {
        var commands: CommandService? = null

        class Context(val cmdName: String) : CommandContext {
            override fun respond(message: String) {
                logger.info("[CMD:$cmdName] $message")
            }
        }
        serviceRegistry.consumeService(CommandService::class.java) {
            commands = it
            commands?.registerCommand("quit", "quit", "Close the program.") { _, _ ->
                Cli.stop()
            }
        }

        GlobalScope.launch {
            try {
                Cli.run { cmdLine ->
                    val cmdName = cmdLine.split(" ")[0]
                    commands?.executeCommand(cmdLine, Context(cmdName))
                }
            } catch (e: Throwable) {
                logger.log(Level.SEVERE, "Error in CLI: $e", e)
            } finally {
                Cli.stop()
                serviceRegistry.teardown()
            }
        }
    }

    override fun teardown(newModule: Module?) {
        Cli.stop()
    }
}
