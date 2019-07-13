package com.github.gjum.minecraft.botlin.behaviors

import com.github.gjum.minecraft.botlin.api.AvatarEvents
import com.github.gjum.minecraft.botlin.api.ServiceRegistry
import com.github.gjum.minecraft.botlin.api.Vec3d
import com.github.gjum.minecraft.botlin.util.ModuleAutoEvents
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerVehicleMovePacket
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerPositionRotationPacket

/**
 * Does simple physics (falling).
 */
open class IdlePhysics : ModuleAutoEvents() {
	override val name = "IdlePhysics"

	private var onGround = false // XXX set inside avatar
	private var prevPos: Vec3d? = null

	override suspend fun activate(serviceRegistry: ServiceRegistry) {
		super.activate(serviceRegistry)
		onEach(AvatarEvents.PreClientTick::class.java, ::doPhysicsTick)
		onEach(AvatarEvents.TeleportedByServer::class.java, ::onTeleportedByServer)
	}

	private fun doPhysicsTick(event: AvatarEvents.PreClientTick) {
		if (!avatar.alive) return
		if (!onGround) {
			prevPos = avatar.entity!!.position!!
			val newPos = prevPos!! - Vec3d(0.0, 1.0 / 8, 0.0)
			avatar.entity!!.position = newPos // XXX setting position should require movement lock
		}
		// TODO apply entity velocity (knockback)
	}

	private fun onTeleportedByServer(event: AvatarEvents.TeleportedByServer) {
		if (event.reason is ServerPlayerPositionRotationPacket) {
			onGround = event.newPos == prevPos
		} else if (event.reason is ServerVehicleMovePacket) {
			onGround = true // in vehicle
		}
	}
}
