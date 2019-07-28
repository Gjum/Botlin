package com.github.gjum.minecraft.botlin.defaults

import com.github.gjum.minecraft.botlin.api.*
import com.github.gjum.minecraft.botlin.modules.ReloadableServiceRegistry
import com.github.gjum.minecraft.botlin.util.toAnsi
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientChatPacket
import kotlinx.coroutines.runBlocking

class UsefulCommandsModule : Module() {
	override suspend fun activate(serviceRegistry: ServiceRegistry) {
		super.activate(serviceRegistry)
		val commands = serviceRegistry.consumeService(CommandService::class.java)
			?: return
		val avatar = serviceRegistry.consumeService(Avatar::class.java)
			?: return

		commands.registerCommand("quit", "Close the program."
		) { _, _ ->
			runBlocking {
				(serviceRegistry as ReloadableServiceRegistry)
					.teardown()
			}
		}
		commands.registerCommand("reload", "Reload all modules."
		) { _, _ ->
			runBlocking {
				(serviceRegistry as ReloadableServiceRegistry)
					.reloadModules(null)
			}
		}
		commands.registerCommand("info", "Show bot info: connection, location, health, etc."
		) { _, context ->
			avatar.run {
				context.respond("${profile.name} at $position $look on ${connection?.remoteAddress}")
				context.respond("health=$health food=$food sat=$saturation exp=${experience?.total} (${experience?.level} lvl)")
			}
		}
		commands.registerCommand("list", "Show connected players list."
		) { _, context ->
			val namesSpaceSep = avatar.playerList!!.values
				.sortedBy { it.profile.name }
				.joinToString(" ") {
					it.displayName?.toAnsi() ?: it.profile.name
				}
			context.respond("Connected players: $namesSpaceSep")
		}
		commands.registerCommand("say <message>", "Send a chat message to the server."
		) { cmdLine, context ->
			val msg = cmdLine.substring("say ".length)
			avatar.connection?.apply { send(ClientChatPacket(msg)) }
				?: context.respond("Not connected to any server")
		}
	}
}
