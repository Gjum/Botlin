package com.github.gjum.minecraft.botlin.behaviors

import com.github.gjum.minecraft.botlin.api.AvatarEvents
import com.github.gjum.minecraft.botlin.api.ServiceRegistry
import com.github.gjum.minecraft.botlin.util.ModuleAutoEvents
import com.github.gjum.minecraft.botlin.util.toAnsi
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Listens to several [AvatarEvents] and logs them in human readable form.
 */
class EventLogger : ModuleAutoEvents() {
	private val logger = Logger.getLogger(this::class.java.name)

	override suspend fun activate(serviceRegistry: ServiceRegistry) {
		super.activate(serviceRegistry)
		onEach(AvatarEvents.Connected::class.java) {
			it.run {
				logger.info("Connected to ${connection.remoteAddress}")
			}
		}
		onEach(AvatarEvents.Disconnected::class.java) {
			it.run {
				if (cause != null) logger.log(Level.FINE, cause.toString(), cause)
				logger.warning("Disconnected from ${connection.remoteAddress} Reason: $reason")
			}
		}
		onEach(AvatarEvents.Spawned::class.java) {
			it.run {
				logger.info("Spawned at ${entity.position} ${entity.look} as eid ${entity.eid}")
			}
		}
		onEach(AvatarEvents.ChatReceived::class.java) {
			it.run {
				logger.info("[CHAT] ${msg.toAnsi()}")
			}
		}
		onEach(AvatarEvents.PlayerJoined::class.java) {
			it.run {
				logger.info("Player joined: ${entry.displayName?.toAnsi() ?: entry.profile.name}")
			}
		}
		onEach(AvatarEvents.PlayerLeft::class.java) {
			it.run {
				logger.info("Player left: ${entry.displayName?.toAnsi() ?: entry.profile.name}")
			}
		}
		onEach(AvatarEvents.WindowReady::class.java) {
			it.run {
				logger.info("Window ready: ${newWindow.windowTitle} with ${newWindow.slotCount} slots")
			}
		}
		onEach(AvatarEvents.WindowClosed::class.java) {
			it.run {
				logger.info("Window closed: ${oldWindow.windowTitle} with ${oldWindow.slotCount} slots")
			}
		}
		onEach(AvatarEvents.TeleportedByServer::class.java) {
			it.run {
				logger.info("Position changed to $newPos")
			}
		}
		onEach(AvatarEvents.SlotsChanged::class.java) {
			it.run {
				logger.info("Slots changed")
			}
		}
		onEach(AvatarEvents.WindowPropertyChanged::class.java) {
			it.run {
				logger.info("Window property changed: $property from $oldValue to $newValue")
			}
		}
		onEach(AvatarEvents.PlayerEntityStatusChanged::class.java) {
			it.run {
				logger.info("Player entity status changed")
			}
		}
	}
}
