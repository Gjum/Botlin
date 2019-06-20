package com.github.gjum.minecraft.botlin.modules.defaults

import com.github.gjum.minecraft.botlin.api.AvatarEvent
import com.github.gjum.minecraft.botlin.api.AvatarEvents
import com.github.gjum.minecraft.botlin.api.EventEmitter
import com.github.gjum.minecraft.botlin.api.Module
import com.github.gjum.minecraft.botlin.modules.ReloadableServiceRegistry
import com.github.gjum.minecraft.botlin.util.EventEmitterImpl
import com.github.gjum.minecraft.botlin.util.toAnsi
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Listens to several [AvatarEvents] and logs them in human readable form.
 */
class EventLoggerModule : Module() {
    private val logger = Logger.getLogger(this::class.java.name)

    override suspend fun initialize(serviceRegistry: ReloadableServiceRegistry, oldModule: Module?) {
        // XXX on every avatar creation: listen for all events
        val avatar = EventEmitterImpl<AvatarEvent>() // XXX on avatar
        registerWithAvatar(avatar)
    }

    fun registerWithAvatar(avatar: EventEmitter<AvatarEvent>) {
        avatar.on(AvatarEvents.Connected::class.java) {
            it.run {
                logger.info("Connected to ${connection.remoteAddress}")
            }
        }
        avatar.on(AvatarEvents.Disconnected::class.java) {
            it.run {
                if (cause != null) logger.log(Level.FINE, cause.toString(), cause)
                logger.warning("Disconnected from ${connection.remoteAddress} Reason: $reason")
            }
        }
        avatar.on(AvatarEvents.Spawned::class.java) {
            it.run {
                logger.info("Spawned at ${entity.position} ${entity.look} as eid ${entity.eid}")
            }
        }
        avatar.on(AvatarEvents.ChatReceived::class.java) {
            it.run {
                logger.info("[CHAT] ${msg.toAnsi()}")
            }
        }
        avatar.on(AvatarEvents.PlayerJoined::class.java) {
            it.run {
                logger.info("Player joined: ${entry.displayName?.toAnsi() ?: entry.profile.name}")
            }
        }
        avatar.on(AvatarEvents.PlayerLeft::class.java) {
            it.run {
                logger.info("Player left: ${entry.displayName?.toAnsi() ?: entry.profile.name}")
            }
        }
        avatar.on(AvatarEvents.WindowReady::class.java) {
            it.run {
                logger.info("Window ready: ${newWindow.windowTitle} with ${newWindow.slotCount} slots")
            }
        }
        avatar.on(AvatarEvents.WindowClosed::class.java) {
            it.run {
                logger.info("Window closed: ${oldWindow.windowTitle} with ${oldWindow.slotCount} slots")
            }
        }
        avatar.on(AvatarEvents.TeleportByServer::class.java) {
            it.run {
                logger.info("Position changed to $newPos")
            }
        }
        avatar.on(AvatarEvents.SlotsChanged::class.java) {
            it.run {
                logger.info("Slots changed")
            }
        }
        avatar.on(AvatarEvents.WindowPropertyChanged::class.java) {
            it.run {
                logger.info("Window property changed: $property from $oldValue to $newValue")
            }
        }
        avatar.on(AvatarEvents.PlayerEntityStatusChanged::class.java) {
            it.run {
                logger.info("Player entity status changed")
            }
        }
    }
}
