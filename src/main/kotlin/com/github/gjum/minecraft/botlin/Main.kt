package com.github.gjum.minecraft.botlin

import com.github.gjum.minecraft.botlin.api.setupBot
import com.github.gjum.minecraft.botlin.behaviors.EventLogger
import com.github.gjum.minecraft.botlin.cli.*
import kotlinx.coroutines.runBlocking

object Main {
	@JvmStatic
	fun main(args: Array<String>) = runBlocking {
		val username = args.getOrNull(0) ?: "Botlin"
		val address = args.getOrNull(1)

		val parentScope = this
		val bot = setupBot(username, listOf(::EventLogger))

		val commandRegistry = CommandRegistryImpl()
		registerUsefulCommands(commandRegistry, bot, parentScope)

		Cli.start {
			handleCommand(it, commandRegistry)
		}

		if (address != null) bot.connect(address)

		Cli.join()

		bot.shutdown()
	}
}
