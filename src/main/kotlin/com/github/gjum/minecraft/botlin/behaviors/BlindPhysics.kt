package com.github.gjum.minecraft.botlin.behaviors

import com.github.gjum.minecraft.botlin.api.AvatarEvents
import com.github.gjum.minecraft.botlin.api.ServiceRegistry
import com.github.gjum.minecraft.botlin.api.Vec3d
import com.github.gjum.minecraft.botlin.util.ModuleAutoEvents
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerVehicleMovePacket
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerPositionRotationPacket

private const val DRAG = 0.98
private const val GRAVITY = 0.08
private const val JUMP_FORCE = 0.42
private const val WALK_SPEED = 4.3 / 20
private const val RUN_SPEED = 5.612 / 20
private const val PIXEL = 1.0 / 16

/**
 * Relies on server's position resets to detect bumping into blocks.
 */
open class BlindPhysics : ModuleAutoEvents() {
	override val name = "BlindPhysics"

	var jumpQueued = false
	var movementTarget: Vec3d? = null

	private var onGround = false
	private var prevPos: Vec3d? = null
	private var velocity = Vec3d(0.0, -PIXEL, 0.0)

	private val falling get() = velocity.y < 0
	private val ascending get() = velocity.y > 0
	private val movingHorizontally get() = velocity.x != 0.0 || velocity.z != 0.0

	private var position
		get() = avatar.entity!!.position!!
		set(p) {
			avatar.entity!!.position = p
		}

	private fun reset() {
		onGround = false
		prevPos = null
		velocity = Vec3d(0.0, -PIXEL, 0.0)
		movementTarget = null
	}

	override suspend fun activate(serviceRegistry: ServiceRegistry) {
		super.activate(serviceRegistry)
		onEach(AvatarEvents.PreClientTick::class.java, ::doPhysicsTick)
		onEach(AvatarEvents.TeleportedByServer::class.java, ::onTeleportedByServer)
	}

	private fun doPhysicsTick(event: AvatarEvents.PreClientTick) {
		if (!avatar.alive) return
		prevPos = position

		// TODO use entity velocity (knockback)
		velocity.y -= GRAVITY
		velocity.y *= DRAG

		if (onGround) {
			if (jumpQueued) velocity.y = JUMP_FORCE
			else velocity.y = 0.0
		}
		// hit the floor at a multiple of 1/16
		// (one "pixel", largest common divisor of all block heights)
		// so we're on the top of a block when our position gets reset (expectedly)
		// round our y pos to a whole number of pixels
		// this is a no-op after the first time, ...
		// position.y -= ((position.y * 16) % 1) / 16;
		// ... because we descend one pixel per tick
		// if (yVel < -PIXEL) yVel = -PIXEL;

		val movementTarget = this.movementTarget
		if (movementTarget != null) {
			var moveVec = movementTarget - position
			moveVec.y = 0.0 // rely on stepping TODO allow floating up water/ladders
			val distToGo = (moveVec - Vec3d.origin).length()
			if (distToGo <= RUN_SPEED) {
				// get there in one step
				position = movementTarget
				// TODO emit arrival
				this.movementTarget = null
			} else {
				moveVec *= (RUN_SPEED / distToGo)
				velocity.x = moveVec.x
				velocity.z = moveVec.z
				position = position + velocity
			}
		}
	}

	/**
	 * possible causes -> possible responses:
	 * - hit floor -> stop falling
	 * - hit ceiling -> stop rising, start falling
	 * - hit wall ->  try stepping | stop moving horizontally
	 * - teleported -> reset
	 */
	private fun onTeleportedByServer(event: AvatarEvents.TeleportedByServer) {
		if (event.reason is ServerVehicleMovePacket) {
			onGround = true // in vehicle
			return
		}
		if (event.reason !is ServerPlayerPositionRotationPacket) return

		val wasInvalidMove = event.newPos == prevPos
		if (!wasInvalidMove) {
			reset() // got teleported
		}

		// TODO emit events, so other module can record environment guesses

		if (movingHorizontally) {
			velocity.x = 0.0
			velocity.z = 0.0
			// TODO when to try stepping? - record vanilla stepping/stairs
		} else {
			if (falling) {
				onGround = true // hit floor
				// TODO emit arrival
			}
			velocity.y = 0.0 // hit ceiling/floor
		}
	}
}
