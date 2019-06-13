package com.github.gjum.minecraft.botlin.modules.defaults

import com.github.gjum.minecraft.botlin.api.AvatarEvents
import com.github.gjum.minecraft.botlin.api.EventEmitter
import com.github.gjum.minecraft.botlin.api.Module
import com.github.gjum.minecraft.botlin.modules.ServiceRegistry
import com.github.gjum.minecraft.botlin.util.EventEmitterImpl
import com.github.gjum.minecraft.botlin.util.Vec3d
import com.github.gjum.minecraft.botlin.util.toAnsi
import com.github.steveice10.packetlib.Session
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Listens to several [AvatarEvents] and logs them in human readable form.
 */
class EventLoggerModule : Module() {
    private val logger = Logger.getLogger(this::class.java.name)

    override suspend fun initialize(serviceRegistry: ServiceRegistry, oldModule: Module?) {
        // XXX on every avatar creation: listen for all events
        val avatar = EventEmitterImpl<AvatarEvents>() // XXX on avatar
        registerWithAvatar(avatar)
    }

    fun registerWithAvatar(avatar: EventEmitter<AvatarEvents>) {
        avatar.on(AvatarEvents.Connected, fun(connection: Session) = logger.info(
            "Connected to ${connection.remoteAddress}"
        ))

        avatar.on(AvatarEvents.Disconnected) { connection, reason, cause ->
            if (cause != null) logger.log(Level.FINE, cause.toString(), cause)
            logger.warning("Disconnected from ${connection.remoteAddress} Reason: $reason")
        }

        avatar.on(AvatarEvents.Spawned) { entity ->
            logger.info("Spawned at ${entity.position} ${entity.look} as eid ${entity.eid}")
        }

        avatar.on(AvatarEvents.ChatReceived) { msg ->
            logger.info("[CHAT] ${msg.toAnsi()}")
        }

        avatar.on(AvatarEvents.PlayerJoined) { entry ->
            logger.info("Player joined: ${entry.displayName?.toAnsi() ?: entry.profile.name}")
        }

        avatar.on(AvatarEvents.PlayerLeft) { entry ->
            logger.info("Player left: ${entry.displayName?.toAnsi() ?: entry.profile.name}")
        }

        avatar.on(AvatarEvents.WindowReady) { window ->
            logger.info("Window ready: ${window.windowTitle} with ${window.slotCount} slots")
        }

        avatar.on(AvatarEvents.WindowClosed) { window ->
            logger.info("Window closed: ${window.windowTitle} with ${window.slotCount} slots")
        }

        avatar.on(AvatarEvents.PositionChanged, fun(newPos: Vec3d, oldPos: Vec3d?, reason: Any?) {
            logger.info("Position changed to $newPos")
        })

        avatar.on(AvatarEvents.SlotsChanged) { window, indices, newSlots, oldSlots ->
            logger.info("Slots changed")
        }

        avatar.on(AvatarEvents.WindowPropertyChanged) { window, property, oldValue, newValue ->
            logger.info("Window property changed: $property from $oldValue to $newValue")
        }

        avatar.on(AvatarEvents.PlayerEntityStatusChanged) {
            logger.info("Player entity status changed")
        }
    }
}
