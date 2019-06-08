package com.github.gjum.minecraft.botlin.modules.defaults

import com.github.gjum.minecraft.botlin.api.Command
import com.github.gjum.minecraft.botlin.api.CommandContext
import com.github.gjum.minecraft.botlin.api.CommandService

class CommandRegistry : CommandService {
    private val commands = mutableMapOf<String, Command>()

    override fun registerCommand(command: Command): Boolean {
        val alreadyRegisteredCmd = commands.putIfAbsent(command.name, command)
        return alreadyRegisteredCmd == null
    }

    override fun executeCommand(commandLine: String, context: CommandContext): Boolean {
        if (commandLine == "") return false
        val isSlashCommand = commandLine.getOrNull(0)?.equals('/') == true
        val cmdLineAdjusted = if (isSlashCommand) "say $commandLine" else commandLine
        val cmdName = cmdLineAdjusted.split(" ")[0]
        val command = commands[cmdName]
        command?.handle(cmdLineAdjusted, context)
        return command != null
    }
}
