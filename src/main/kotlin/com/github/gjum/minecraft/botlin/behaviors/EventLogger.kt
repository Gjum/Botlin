package com.github.gjum.minecraft.botlin.behaviors

import com.github.gjum.minecraft.botlin.api.AvatarEvent
import com.github.gjum.minecraft.botlin.api.ServiceRegistry
import com.github.gjum.minecraft.botlin.util.AutoEventsModule
import com.github.gjum.minecraft.botlin.util.toAnsi
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Listens to several [AvatarEvent] and logs them in human readable form.
 */
class EventLogger : AutoEventsModule() {
	private val logger = Logger.getLogger(this::class.java.name)

	override suspend fun activate(serviceRegistry: ServiceRegistry) {
		super.activate(serviceRegistry)
		onEach(AvatarEvent.Connected::class.java) {
			it.run {
				logger.info("Connected to ${connection.remoteAddress}")
			}
		}
		onEach(AvatarEvent.Disconnected::class.java) {
			it.run {
				if (cause != null) logger.log(Level.FINE, cause.toString(), cause)
				logger.warning("Disconnected from ${connection.remoteAddress} Reason: $reason")
			}
		}
		onEach(AvatarEvent.Spawned::class.java) {
			it.run {
				logger.info("Spawned at ${entity.position} ${entity.look} as eid ${entity.eid}")
			}
		}
		onEach(AvatarEvent.ChatReceived::class.java) {
			it.run {
				logger.info("[CHAT] ${msg.toAnsi()}")
			}
		}
		onEach(AvatarEvent.PlayerJoined::class.java) {
			it.run {
				logger.info("Player joined: ${entry.displayName?.toAnsi() ?: entry.profile.name}")
			}
		}
		onEach(AvatarEvent.PlayerLeft::class.java) {
			it.run {
				logger.info("Player left: ${entry.displayName?.toAnsi() ?: entry.profile.name}")
			}
		}
		onEach(AvatarEvent.WindowReady::class.java) {
			it.run {
				logger.info("Window ready: ${newWindow.windowTitle} with ${newWindow.slotCount} slots")
			}
		}
		onEach(AvatarEvent.WindowClosed::class.java) {
			it.run {
				logger.info("Window closed: ${oldWindow.windowTitle} with ${oldWindow.slotCount} slots")
			}
		}
		onEach(AvatarEvent.TeleportedByServer::class.java) {
			it.run {
				logger.info("Position changed to $newPos")
			}
		}
		onEach(AvatarEvent.SlotsChanged::class.java) {
			it.run {
				logger.info("Slots changed")
			}
		}
		onEach(AvatarEvent.WindowPropertyChanged::class.java) {
			it.run {
				logger.info("Window property changed: $property from $oldValue to $newValue")
			}
		}
		onEach(AvatarEvent.PlayerEntityStatusChanged::class.java) {
			it.run {
				logger.info("Player entity status changed")
			}
		}
	}
}
