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
import com.github.gjum.minecraft.botlin.util.Log
import com.github.gjum.minecraft.botlin.util.toAnsi
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientChatPacket
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

object Main {
	@JvmStatic
	fun main(args: Array<String>) {
		Log.reload() // TODO why is this needed? JUL should automatically read logging.properties
		val modulesLoader = StaticModulesLoader(
			setupDefaultModules() + MainArgsModule(args))
		val serviceRegistry = ReloadableServiceRegistry(modulesLoader)
		GlobalScope.launch { serviceRegistry.reloadModules(null) }
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
	IdlePhysics(),
	UsefulCommandsModule()
)

interface MainArgs : Service {
	val args: Array<String>
}

private class MainArgsModule(private val args: Array<String>) : Module() {
	override suspend fun activate(serviceRegistry: ServiceRegistry) {
		serviceRegistry.provideService(MainArgs::class.java, object : MainArgs {
			override val args get() = this@MainArgsModule.args
		})
	}
}

class UsefulCommandsModule : Module() {
	override suspend fun activate(serviceRegistry: ServiceRegistry) {
		super.activate(serviceRegistry)
		val commands = serviceRegistry.consumeService(CommandService::class.java)
			?: return
		val avatar = serviceRegistry.consumeService(Avatar::class.java)
			?: return

		commands.registerCommand("info", "info",
			"Show bot info: connection, location, health, etc."
		) { _, context ->
			avatar.run {
				context.respond("${profile.name} at $position $look on ${connection?.remoteAddress}")
				context.respond("health=$health food=$food sat=$saturation exp=${experience?.total} (${experience?.level} lvl)")
			}
		}
		commands.registerCommand("list", "list",
			"Show connected players list."
		) { _, context ->
			val namesSpaceSep = avatar.playerList!!.values
				.sortedBy { it.profile.name }
				.joinToString(" ") {
					it.displayName?.toAnsi() ?: it.profile.name
				}
			context.respond("Connected players: $namesSpaceSep")
		}
		commands.registerCommand("say", "say <message>",
			"Send a chat message to the server."
		) { cmdLine, context ->
			val msg = cmdLine.substring("say ".length)
			avatar.connection?.apply { send(ClientChatPacket(msg)) }
				?: context.respond("Not connected to any server")
		}
	}
}

private val commandLogger = Logger.getLogger("com.github.gjum.minecraft.botlin.Commands")

private class LoggingCommandContext(val cmdName: String) : CommandContext {
	override fun respond(message: String) {
		commandLogger.info("[$cmdName] $message")
	}
}

private fun trySetupCli(services: ServiceRegistry) {
	val commands = runBlocking {
		services.consumeService(CommandService::class.java)
	} ?: return // no commands, so no cli
	try {
		commands.registerCommand("quit", "quit", "Close the program.") { _, _ ->
			Cli.stop()
		}
		Cli.run { cmdLineRaw ->
			if (cmdLineRaw.trim().isEmpty()) return@run
			val isSlashCommand = cmdLineRaw.getOrNull(0)?.equals('/') == true
			val cmdLine = if (isSlashCommand) "say $cmdLineRaw" else cmdLineRaw
			val cmdName = cmdLine.split(' ')[0]
			val context = LoggingCommandContext(cmdName)
			try {
				if (!commands.executeCommand(cmdLine, context)) {
					commandLogger.warning("Unknown command: $cmdLine")
				}
			} catch (e: Exception) {
				context.respond("Error while running command: $e")
				commandLogger.log(Level.WARNING, "Error while running command $cmdName", e)
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
