package com.github.gjum.minecraft.botlin.behaviors

import com.github.gjum.minecraft.botlin.api.*
import com.github.gjum.minecraft.botlin.impl.Physics
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerPositionRotationPacket
import com.github.steveice10.mc.protocol.packet.ingame.client.world.ClientTeleportConfirmPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerVehicleMovePacket
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerPositionRotationPacket
import kotlinx.coroutines.launch
import java.util.logging.Logger

/**
 * Fall straight down until the server resets the position,
 * indicating landing on the floor.
 */
class IdlePhysics(private val bot: ApiBot) : ChildScope(bot), Physics {
	override var movementSpeed = 0.0
		set(_) = error("IdlePhysics don't allow movement")

	init {
		launch { bot.onEach(::doPhysicsTick) }
		launch { bot.onEach(::onTeleportedByServer) }
	}

	override suspend fun moveStraightTo(destination: Vec3d): Result<Route, MoveError> {
		error("IdlePhysics don't allow movement")
	}

	override suspend fun jump() {
		error("IdlePhysics don't allow movement")
	}

	private val entity get() = bot.avatar.playerEntity!!

	private fun doPhysicsTick(event: AvatarEvent.PreClientTick) {
		if (!bot.alive || entity.onGround!!) return
		entity.apply {
			// TODO apply entity velocity (knockback)
			val velY = ((velocity!!.y - GRAVITY) * DRAG)
				.coerceAtLeast(-1.0 / 16) // fall at most one pixel to land perfectly on the floor
			velocity = velocity!!.copy(y = velY)
			position = position!! + velocity!!
		}
	}

	private fun onTeleportedByServer(event: AvatarEvent.TeleportedByServer) {
		if (event.packet is ServerVehicleMovePacket) {
			// in vehicle
			entity.onGround = true
			entity.velocity = Vec3d.origin
			return
		}
		if (event.packet !is ServerPlayerPositionRotationPacket) {
			Logger.getLogger("IdlePhysics").warning("Received unexpected TeleportedByServer packet: ${event.packet}")
			return
		}

		bot.sendPacket(ClientTeleportConfirmPacket(event.packet.teleportId))
		entity.apply {
			bot.sendPacket(
				ClientPlayerPositionRotationPacket(
					(onGround ?: true),
					position!!.x,
					position!!.y,
					position!!.z,
					look!!.yawDegrees.toFloat(),
					look!!.pitchDegrees.toFloat()
				)
			)

			velocity = Vec3d.origin
			onGround = onGround == false // when unset, start falling
		}
	}
}
