package com.github.gjum.minecraft.botlin.modules.defaults

import com.github.gjum.minecraft.botlin.api.Command
import com.github.gjum.minecraft.botlin.api.CommandContext
import com.github.gjum.minecraft.botlin.api.CommandService
import com.github.gjum.minecraft.botlin.api.Module

class CommandRegistry : CommandService {
    private val commands = mutableMapOf<String, Command>()

    override fun registerCommand(command: Command): Boolean {
        val alreadyRegisteredCmd = commands.putIfAbsent(command.prefix, command)
        return alreadyRegisteredCmd == null
    }

    override fun executeCommand(commandLine: String, context: CommandContext): Boolean {
        if (commandLine == "") return false
        val prefix = commandLine.split(" ")[0]
        val command = commands[prefix]
        command?.handle(commandLine, context)
        return command != null
    }
}
