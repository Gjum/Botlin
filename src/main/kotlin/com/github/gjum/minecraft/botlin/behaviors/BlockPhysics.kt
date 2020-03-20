/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package com.github.gjum.minecraft.botlin.behaviors

import com.github.gjum.minecraft.botlin.api.*
import com.github.gjum.minecraft.botlin.util.Ray
import com.github.gjum.minecraft.botlin.util.calculateIntercept
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerPositionRotationPacket
import com.github.steveice10.mc.protocol.packet.ingame.client.world.ClientTeleportConfirmPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerVehicleMovePacket
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerPositionRotationPacket
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.logging.Logger
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs

/**
 * Uses blocks' collision boxes from mc-data to check movement validity.
 */
class BlockPhysics(private val bot: ApiBot) : ChildScope(bot) {
	var movementSpeed = RUN_SPEED

	var movementTarget: Vec3d? = null
	private var arrivalContinuation: CancellableContinuation<Result<Route, MoveError>>? = null

	private var jumpQueued = false
	private var jumpLandedContinuation: Continuation<Unit>? = null

	private var onGround = false
	private var velocity = Vec3d.origin // current m/t to move in each direction next tick

	private val avatar get() = bot.avatar

	private var position: Vec3d
		get() = bot.playerEntity!!.position!!
		set(p) {
			avatar.playerEntity!!.position = p
		}

	init {
		launch { bot.onEach(::doPhysicsTick) }
		launch { bot.onEach(::onTeleportedByServer) }
	}

	private fun reset() {
		onGround = false
		// start in falling-only state
		velocity = Vec3d.origin
		movementTarget = null
		arrivalContinuation?.cancel(MoveError("Physics Reset"))
		arrivalContinuation = null
	}

	// TODO make sure other calls finish before this runs; @Synchronized doesn't work like that, synchronized(){} is deprecated
	suspend fun moveStraightTo(destination: Vec3d): Result<Route, MoveError> {
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
	suspend fun jump() {
		if (!onGround) throw JumpError("Tried to jump while not standing on ground")
		return suspendCoroutine { cont ->
			jumpLandedContinuation = cont
			jumpQueued = true
		}
	}

	private fun doPhysicsTick(event: AvatarEvent.PreClientTick) {
		if (!bot.alive) return
		val movementTarget = this.movementTarget

		// TODO wait for next tick before comparing position to target! this assumes the target is reachable. what if we hit a wall or otherwise get a movement reset? should we keep pursuing the target?
		if (movementTarget != null && (position - movementTarget).lengthSquared() < VERY_CLOSE) {
			this.movementTarget = null
			arrivalContinuation?.resume(Result.Success(Unit))
		}

		if (jumpQueued && onGround) {
			jumpQueued = false
			velocity = velocity.withAxis(Axis.Y, JUMP_FORCE)
		}
		// XXX apply entity velocity (knockback)
		// TODO floating up water/ladders

		var moveHorizVec = Vec3d.origin
		if (movementTarget != null) {
			// movementTarget only influences x,z; rely on stepping/falling to change y
			moveHorizVec = (movementTarget - position).withAxis(Axis.Y, 0.0)
			val moveHorizVecLen = (moveHorizVec - Vec3d.origin).length()
			if (moveHorizVecLen > movementSpeed) {
				moveHorizVec *= movementSpeed / moveHorizVecLen
			}
			// else: get there in one step
		}

		val velY = (velocity.y - GRAVITY) * DRAG
		// movementTarget only influences x,z; rely on stepping/falling to change y
		velocity = Vec3d(moveHorizVec.x, velY, moveHorizVec.z)

		// calculate collisions and adjusted position

		val startBox = playerBox + position

		val movementBoxOrig = Shape(listOf(startBox, startBox + velocity)).outerBox!!
		val movementBox = Box(movementBoxOrig.min, movementBoxOrig.max.withAxis(Axis.Y, movementBoxOrig.max.y + STEPPING_HEIGHT)) // add stepping height so we can reuse the obstacles when stepping
		val obstacles = mutableListOf<Box>()
		for (z in movementBox.min.z.floor..movementBox.max.z.floor) {
			for (x in movementBox.min.x.floor..movementBox.max.x.floor) {
				for (y in movementBox.min.y.floor..movementBox.max.y.floor) {
					val blockState = avatar.world?.getBlockState(Vec3i(x, y, z))
						?: continue // outside world or outside loaded chunks
					val blockStateInfo = bot.mcData.getBlockStateInfo(blockState)
						?: error("Unknown block state $blockState")
					obstacles.addAll(blockStateInfo.collisionShape.boxes)
				}
			}
		}

		var (newBox, collisions) = calcMoveDest(velocity, startBox, obstacles)

		var bumpedIntoWall = collisions.find { it.face.axis != Axis.Y }

		if (bumpedIntoWall != null) {
			// TODO maybe not +STEPPING_HEIGHT but set feet to the top edge of the collided block if steppable
			val startBoxStepping = startBox + Vec3d(0.0, STEPPING_HEIGHT, 0.0)
			val moveVecStepping = velocity.copy(y = 0.0) // gravity would pull us into the step, preventing stepping
			val (newBoxStepping, collisionsStepping) = calcMoveDest(moveVecStepping, startBoxStepping, obstacles)
			// consider stepping preferable if at least one movement component is larger then without stepping
			val (sx, sy, sz) = newBoxStepping.min - startBoxStepping.min
			val (nx, ny, nz) = newBox.min - startBox.min
			val stepIsValid = abs(sx) > abs(nx) || abs(sy) > abs(ny) || abs(sz) > abs(nz)
			if (stepIsValid) {
				newBox = newBoxStepping
				collisions = collisionsStepping
				bumpedIntoWall = collisions.find { it.face.axis != Axis.Y }
			}
		}

		val bumpedIntoCeiling = collisions.find { it.face == Cardinal.DOWN }
		val bumpedIntoFloor = collisions.find { it.face == Cardinal.UP }

		if (bumpedIntoCeiling != null || bumpedIntoFloor != null) {
			velocity = velocity.withAxis(Axis.Y, 0.0)

			if (bumpedIntoFloor != null) jumpLandedContinuation?.resume(Unit)
		}
		// TODO if (bumpedIntoWall) stopSprinting()

		position = newBox.min - playerBox.min
		onGround = bumpedIntoFloor != null
	}

	private fun onTeleportedByServer(event: AvatarEvent.TeleportedByServer) {
		if (event.packet is ServerVehicleMovePacket) {
			// in vehicle
			onGround = true
			velocity = Vec3d.origin
			return
		}
		if (event.packet !is ServerPlayerPositionRotationPacket) {
			Logger.getLogger("Physics").warning("Received unexpected TeleportedByServer packet: ${event.packet}")
			return
		}

		bot.post(ClientTeleportConfirmPacket(event.packet.teleportId))
		bot.playerEntity!!.apply {
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

		}

		reset()
	}
}

data class Collision(val box: Box, val face: Cardinal)
data class MoveAdjustResult(val endBox: Box, val collisions: Collection<Collision>)

fun calcMoveDest(moveVec: Vec3d, source: Box, obstacles: Collection<Box>): MoveAdjustResult {
	val sourceSize = source.size
	val obstaclesGrown = obstacles
		.filter { !it.intersects(source) }
		.map { Box(it.min - sourceSize, it.max) }

	val collisions = mutableListOf<Collision>()

	var moveVecCurr = moveVec
	var startPosCurr = source.min
	var endPosCurr = startPosCurr + moveVecCurr // maximum movement

	for (i in 1..3) {
		val ray = Ray(startPosCurr, moveVecCurr)
		var closestCollision: Collision? = null
		for (obstacle in obstaclesGrown) {
			val (intercept, facing) = obstacle.calculateIntercept(ray) ?: continue
			if ((endPosCurr - startPosCurr).lengthSquared() > (intercept - startPosCurr).lengthSquared()) {
				endPosCurr = intercept
				closestCollision = Collision(obstacle, facing)
			}
		}

		if (closestCollision == null) break

		// prepare next iteration
		moveVecCurr -= endPosCurr - startPosCurr
		moveVecCurr = moveVecCurr.withAxis(closestCollision.face.axis, 0.0)
		startPosCurr = endPosCurr
		endPosCurr = startPosCurr + moveVecCurr // maximum movement
	}

	val endBox = source + (endPosCurr - source.min)
	return MoveAdjustResult(endBox, collisions)
}
