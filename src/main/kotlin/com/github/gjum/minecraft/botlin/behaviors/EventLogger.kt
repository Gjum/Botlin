package com.github.gjum.minecraft.botlin.behaviors

import com.github.gjum.minecraft.botlin.api.ApiBot
import com.github.gjum.minecraft.botlin.api.AvatarEvent
import com.github.gjum.minecraft.botlin.api.Behavior
import com.github.gjum.minecraft.botlin.api.onEach
import com.github.gjum.minecraft.botlin.util.toAnsi
import kotlinx.coroutines.launch
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Listens to several [AvatarEvent] and logs them in human readable form.
 */
class EventLogger(bot: ApiBot) : Behavior(bot) {
	private val logger = Logger.getLogger(this::class.java.name)

	private inline fun <reified E : AvatarEvent> setup(crossinline handler: E.() -> Unit) {
		launch { bot.onEach { e: E -> e.handler() } }
	}

	init {
		setup<AvatarEvent.Connected> {
			logger.info("Connected to ${connection.remoteAddress}")
		}
		setup<AvatarEvent.Disconnected> {
			logger.log(Level.WARNING, "Disconnected from ${connection.remoteAddress} Reason: $reason Cause: $cause", cause)
		}
		setup<AvatarEvent.Spawned> {
			logger.info("Spawned at ${entity.position} ${entity.look} as eid ${entity.eid}")
		}
		setup<AvatarEvent.ChatReceived> {
			logger.info("[CHAT] ${msg.toAnsi()}")
		}
		setup<AvatarEvent.PlayerJoined> {
			logger.info("Player joined: ${entry.displayName?.toAnsi() ?: entry.profile.name}")
		}
		setup<AvatarEvent.PlayerLeft> {
			logger.info("Player left: ${entry.displayName?.toAnsi() ?: entry.profile.name}")
		}
		setup<AvatarEvent.WindowReady> {
			logger.info("Window ready: ${newWindow.windowTitle} with ${newWindow.slots.size} slots")
		}
		setup<AvatarEvent.WindowClosed> {
			logger.info("Window closed: ${oldWindow.windowTitle} with ${oldWindow.slots.size} slots")
		}
		setup<AvatarEvent.TeleportedByServer> {
			logger.warning("Position changed by server to $newPos")
		}
		setup<AvatarEvent.SlotsChanged> {
			logger.info("Slots changed")
		}
		setup<AvatarEvent.WindowPropertyChanged> {
			logger.info("Window property changed: $property from $oldValue to $newValue")
		}
		setup<AvatarEvent.PlayerEntityStatusChanged> {
			logger.info("Player entity status changed")
		}
	}
}
