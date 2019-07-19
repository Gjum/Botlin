package com.github.gjum.minecraft.botlin.behaviors

import com.github.gjum.minecraft.botlin.api.*
import com.github.gjum.minecraft.botlin.util.ModuleAutoEvents
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerVehicleMovePacket
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerPositionRotationPacket
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.math.ceil

private const val DRAG = 0.98 // y velocity multiplicator per tick when not on ground
private const val GRAVITY = 0.08 // m/t²; subtracted from y velocity per tick when not on ground
private const val JUMP_FORCE = 0.42 // m/t; applied to velocity when starting a jump
private const val WALK_SPEED = 4.3 / 20
private const val RUN_SPEED = 5.612 / 20
private const val PIXEL_SIZE = 1.0 / 16 // largest common divisor of all block heights

/**
 * Relies on server's position resets to detect bumping into blocks.
 */
open class BlindPhysics : ModuleAutoEvents(), PhysicsService {
	override val name = "BlindPhysics"

	var movementTarget: Vec3d? = null

	private var arrivalContinuation: CancellableContinuation<Result<Route, MoveError>>? = null
	private var jumpLandedContinuation: CancellableContinuation<Unit>? = null

	private var onGround = false
	private var prevPos: Vec3d? = null
	private var velocity = Vec3d(0.0, -PIXEL_SIZE, 0.0) // current m/t to move in each direction next tick

	private val falling get() = velocity.y < 0
	private val rising get() = velocity.y > 0
	private val movingHorizontally get() = velocity.x != 0.0 || velocity.z != 0.0
	private var jumpQueued = false
	private var stepping = false

	private var position
		get() = avatar.position!!
		set(p) {
			avatar.position = p
		}

	private fun reset() {
		onGround = false
		prevPos = null
		// start in falling-only state
		velocity = Vec3d(0.0, -PIXEL_SIZE, 0.0)
		movementTarget = null
	}

	// TODO make sure other calls finish before this runs; @Synchronized doesn't work like that, synchronized(){} is deprecated
	override suspend fun moveStraightTo(destination: Vec3d): Result<Route, MoveError> {
		arrivalContinuation?.resume(Result.Success(Unit))
		arrivalContinuation = null
		return suspendCancellableCoroutine { cont ->
			arrivalContinuation = cont
			movementTarget = destination
		}
	}

	/**
	 * state -> new state:
	 * - standing -> rising
	 * - moving-only -> rising-and-moving
	 * - stepping -> jump with force 0.5?
	 * - TODO rising-/falling-only/-and-moving -> same state; throw error? | queue jump?
	 */
	// TODO make sure other calls finish before this runs; @Synchronized doesn't work like that, synchronized(){} is deprecated
	override suspend fun jump() {
		if (!onGround) throw Error("Tried jumping while not standing on ground")
		return suspendCancellableCoroutine { cont ->
			jumpLandedContinuation = cont
			jumpQueued = true
		}
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
		if (rising) {
			velocity.y -= GRAVITY
			velocity.y *= DRAG
		}
		if (falling) {
			// hit the floor at a multiple of 1/16
			// (one "pixel", smallest common divisor of all block heights)
			// so we're on the top of a block once our position gets reset (landed)
			val yNextTarget = (ceil(position.y / PIXEL_SIZE) - 1) * PIXEL_SIZE
			if (velocity.y < -PIXEL_SIZE) velocity.y = yNextTarget - position.y
		}

		if (onGround) {
			if (jumpQueued) {
				velocity.y = JUMP_FORCE
//				jumpQueued = false // this would not unset it if we tried to jump in mid-air
			} else {
				velocity.y = 0.0
			}
		}
		jumpQueued = false

		if (stepping) {
			velocity.y = 0.5
		}

		val movementTarget = this.movementTarget
		if (movementTarget != null) {
			var moveVec = movementTarget - position
			moveVec.y = 0.0 // rely on stepping TODO allow floating up water/ladders
			val distToGo = (moveVec - Vec3d.origin).length()
			if (distToGo <= RUN_SPEED) {
				// TODO this assumes the target is reachable. what if we get a movement reset?
				// get there in one step
				position = movementTarget
				this.movementTarget = null
				arrivalContinuation?.resume(Result.Success(Unit))
			} else {
				moveVec *= (RUN_SPEED / distToGo)
				velocity.x = moveVec.x
				velocity.z = moveVec.z
				position = position + velocity
			}
		}

		if (stepping) velocity.y = 0.0
	}

	private fun onTeleportedByServer(event: AvatarEvents.TeleportedByServer) {
		stepping = false
		if (event.reason is ServerVehicleMovePacket) {
			// in vehicle
			onGround = true
			stepping = false
			velocity = Vec3d.origin
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
			if (!stepping) {
				// XXX only when not jumping
				stepping = true
			} else {
				// hit a wall, movement failed
				stepping = false
				movementTarget = null
				arrivalContinuation?.resume(Result.Failure(MoveError()))
			}
		} else {
			stepping = false
		}

		if (falling) {
			onGround = true // hit floor
			jumpLandedContinuation?.resume(Unit)
		}

		velocity.y = 0.0 // hit ceiling/floor
	}
}
