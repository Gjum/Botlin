package com.github.gjum.minecraft.botlin.modules.defaults

import com.github.gjum.minecraft.botlin.api.Module
import com.github.gjum.minecraft.botlin.modules.ServiceRegistry
import com.github.gjum.minecraft.botlin.modules.defaults.commands.CommandService

class CommandModule : Module() {
    override val name = "Commands"
    override val description = "Provides command registration and execution"

    override fun initialize(serviceRegistry: ServiceRegistry, oldModule: Module?) {
        val commandRegistry = CommandRegistry()
        serviceRegistry.provideService(this, CommandService::class.java, commandRegistry)
    }
}
