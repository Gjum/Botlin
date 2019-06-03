package com.github.gjum.minecraft.botlin.modules.defaults

import com.github.gjum.minecraft.botlin.api.Module
import com.github.gjum.minecraft.botlin.modules.defaults.commands.Command
import com.github.gjum.minecraft.botlin.modules.defaults.commands.CommandContext
import com.github.gjum.minecraft.botlin.modules.defaults.commands.CommandService

class CommandRegistry : CommandService {
    private val modules = mutableMapOf<Module, MutableCollection<String>>()
    private val commands = mutableMapOf<String, Command>()

    override fun registerCommand(module: Module, command: Command): Boolean {
        val alreadyRegisteredCmd = commands.putIfAbsent(command.prefix, command)
        return if (alreadyRegisteredCmd == null) {
            modules.getOrPut(module) { mutableListOf() }.add(command.prefix)
            true
        } else false
    }

    override fun executeCommand(commandLine: String, context: CommandContext): Boolean {
        if (commandLine == "") return false
        val prefix = commandLine.split(" ")[0]
        val command = commands[prefix]
        command?.handle(commandLine, context)
        return command != null
    }
}
