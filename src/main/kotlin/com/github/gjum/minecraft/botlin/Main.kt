package com.github.gjum.minecraft.botlin

import com.github.gjum.minecraft.botlin.api.*
import com.github.gjum.minecraft.botlin.behaviors.AutoReconnect
import com.github.gjum.minecraft.botlin.behaviors.EventLogger
import com.github.gjum.minecraft.botlin.behaviors.IdlePhysics
import com.github.gjum.minecraft.botlin.defaults.AuthModule
import com.github.gjum.minecraft.botlin.defaults.AvatarModule
import com.github.gjum.minecraft.botlin.defaults.CommandModule
import com.github.gjum.minecraft.botlin.modules.ModulesLoader
import com.github.gjum.minecraft.botlin.modules.ReloadableServiceRegistry
import com.github.gjum.minecraft.botlin.modules.consumeService
import com.github.gjum.minecraft.botlin.util.Cli
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

object Main {
	@JvmStatic
	fun main(args: Array<String>) {
		val modulesLoader = StaticModulesLoader(
			setupDefaultModules() + MainArgsModule(args))
		val serviceRegistry = ReloadableServiceRegistry(modulesLoader)

		trySetupCli(serviceRegistry)
	}
}

private class StaticModulesLoader(private val modules: Collection<Module>) : ModulesLoader<Module> {
	override fun reload(modulesDir: File?): Collection<Module>? = modules
	override fun getAvailableModules(): Collection<Module> = modules
}

private fun setupDefaultModules() = listOf(
	AuthModule(),
	AutoReconnect(),
	AvatarModule(),
	CommandModule(),
	EventLogger(),
	IdlePhysics()
)

interface MainArgs : Service {
	fun getArgs(): Array<String>
}

private class MainArgsModule(private val args: Array<String>) : Module() {
	override suspend fun activate(serviceRegistry: ServiceRegistry) {
		serviceRegistry.provideService(MainArgs::class.java, object : MainArgs {
			override fun getArgs() = args
		})
	}
}

private val commandLogger = Logger.getLogger("Commands")

private class LoggingCommandContext(val cmdName: String) : CommandContext {
	override fun respond(message: String) {
		commandLogger.info("[$cmdName] $message")
	}
}

private fun trySetupCli(services: ServiceRegistry) {
	val commands = runBlocking {
		services.consumeService(CommandService::class.java)
	} ?: return
	try {
		Cli.run { cmdLine ->
			if (cmdLine.trim().isEmpty()) return@run
			val cmdName = cmdLine.split(' ')[0]
			if (!commands.executeCommand(cmdLine, LoggingCommandContext(cmdName))) {
				commandLogger.warning("Unknown command: $cmdLine")
			}
		}
	} catch (e: Throwable) {
		commandLogger.log(Level.SEVERE, "Error in CLI: $e", e)
		e.printStackTrace()
	} finally {
		Cli.stop()
	}
	// TODO emit some endRequested event
}
