package com.github.gjum.minecraft.botlin.defaults

import com.github.gjum.minecraft.botlin.api.CommandService
import com.github.gjum.minecraft.botlin.api.Module
import com.github.gjum.minecraft.botlin.api.ServiceRegistry

class CommandModule : Module() {
	override val name = "Commands"
	override val description = "Provides command registration and execution"

	override suspend fun activate(serviceRegistry: ServiceRegistry) {
		val commandRegistry = CommandRegistry()
		serviceRegistry.provideService(CommandService::class.java, commandRegistry)
	}
}
