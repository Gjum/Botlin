/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package com.github.gjum.minecraft.botlin.behaviors

import com.github.gjum.minecraft.botlin.api.*
import com.github.gjum.minecraft.botlin.impl.Physics
import com.github.gjum.minecraft.botlin.util.Ray
import com.github.gjum.minecraft.botlin.util.calculateIntercept
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerMovementPacket
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerPositionPacket
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerPositionRotationPacket
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerRotationPacket
import com.github.steveice10.mc.protocol.packet.ingame.client.world.ClientTeleportConfirmPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerVehicleMovePacket
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerPositionRotationPacket
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.logging.Logger
import kotlin.coroutines.resume
import kotlin.math.abs

/**
 * Uses blocks' collision boxes from mc-data to check movement validity.
 */
class BlockPhysics(private val bot: ApiBot) : ChildScope(bot), Physics {
	override var movementSpeed = RUN_SPEED

	var movementTarget: Vec3d? = null
		private set

	private var arrivalContinuation: CancellableContinuation<Route>? = null

	private var jumpQueued = false
	private var jumpLandedContinuation: CancellableContinuation<Unit>? = null

	private val avatar get() = bot.avatar

	private var onGround: Boolean
		get() = avatar.playerEntity!!.onGround ?: false
		set(b) {
			avatar.playerEntity!!.onGround = b
		}

	private var position: Vec3d
		get() = bot.playerEntity!!.position!!
		set(p) {
			avatar.playerEntity!!.position = p
		}

	private var velocity: Vec3d
		get() = avatar.playerEntity!!.velocity ?: Vec3d.origin
		set(v) {
			avatar.playerEntity!!.velocity = v
		}

	private var look: Look
		get() = avatar.playerEntity!!.look ?: Look.origin
		set(l) {
			avatar.playerEntity!!.look = l
		}

	private var prevPos: Vec3d? = null
	private var prevLook: Look? = null

	init {
		launch { bot.onEach(::doPhysicsTick) }
		launch { bot.onEach(::onTeleportedByServer) }
	}

	private fun reset() {
		onGround = false
		velocity = Vec3d.origin
		movementTarget = null
		arrivalContinuation?.cancel(MoveError("Physics Reset"))
		arrivalContinuation = null
		jumpLandedContinuation?.cancel(MoveError("Physics Reset"))
		jumpLandedContinuation = null
	}

	override suspend fun moveStraightTo(destination: Vec3d): Result<Route, MoveError> {
		arrivalContinuation?.cancel(MoveError("Destination changed to $destination"))
		arrivalContinuation = null
		return try {
			Result.Success(suspendCancellableCoroutine { cont ->
				arrivalContinuation = cont
				movementTarget = destination
				cont.invokeOnCancellation {
					if (movementTarget == destination) movementTarget = null
				}
			})
		} catch (e: MoveError) {
			Result.Failure(e)
		}
	}

	override suspend fun jump() {
		if (!onGround) throw JumpError("Tried to jump while not standing on ground")
		if (jumpLandedContinuation != null) throw JumpError("Tried to jump twice at the same time")
		jumpQueued = true
		return suspendCancellableCoroutine { cont ->
			jumpLandedContinuation = cont
		}
	}

	private fun doPhysicsTick(event: AvatarEvent.ClientTick) {
		if (!bot.alive) return
		val movementTarget = this.movementTarget

		var moveHorizVec = Vec3d.origin
		if (movementTarget != null) {
			// movementTarget only influences x,z; rely on stepping/falling to change y
			moveHorizVec = (movementTarget - position).copy(y = 0.0)
			if (moveHorizVec.lengthSquared() < VERY_CLOSE) {
				this.movementTarget = null
				arrivalContinuation?.resume(Route)
				moveHorizVec = Vec3d.origin
			} else {
				println("raw moveHorizVec = $moveHorizVec")
				val moveHorizVecLen = (moveHorizVec - Vec3d.origin).length()
				if (moveHorizVecLen > movementSpeed) {
					// take one step of the length of movementSpeed
					moveHorizVec *= movementSpeed / moveHorizVecLen
				} // else: get there in one step
			}
		}

		var velY = (velocity.y - GRAVITY) * DRAG

		if (jumpQueued && onGround) {
			jumpQueued = false
			velY = JUMP_FORCE
		}
		// TODO floating up water/ladders

		// movementTarget only influences x,z; rely on stepping/falling to change y
		velocity = Vec3d(moveHorizVec.x, velY, moveHorizVec.z)

		// calculate collisions and adjusted position

		val startBox = playerBox + position

		val movementBoxOrig = Shape(listOf(startBox, startBox + velocity)).outerBox!!
		val movementBox = Box(movementBoxOrig.min, movementBoxOrig.max.copy(y = movementBoxOrig.max.y + STEPPING_HEIGHT)) // add stepping height so we can reuse the obstacles when stepping
		val obstacles = mutableListOf<Box>()
		for (z in movementBox.min.z.floor..movementBox.max.z.floor) {
			for (x in movementBox.min.x.floor..movementBox.max.x.floor) {
				for (y in movementBox.min.y.floor..movementBox.max.y.floor) {
					val blockState = avatar.world?.getBlockState(Vec3i(x, y, z))
						?: continue // outside world or outside loaded chunks
					val blockStateInfo = bot.mcData.getBlockStateInfo(blockState)
						?: error("Unknown block state $blockState")
					val boxes = blockStateInfo.collisionShape.boxes
					// move boxes to their position in the world
					val movedBoxes = boxes.map { it + Vec3i(x, y, z).asVec3d }
					obstacles.addAll(movedBoxes)
				}
			}
		}

		var (newBox, collisions) = calcMoveDest(velocity, startBox, obstacles)

		var bumpedIntoWall = collisions.find { it.face.axis != Axis.Y }

		// try stepping, update collisions
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
			velocity = velocity.copy(y = 0.0)

			if (bumpedIntoFloor != null) {
				jumpLandedContinuation?.resume(Unit)
				jumpLandedContinuation = null
			}
		}
		position = newBox.min - playerBox.min
		onGround = bumpedIntoFloor != null

		if (position.anyNaN()) {
			Logger.getLogger("Physics").warning(
				"NaN in position $position, velocity $velocity")
			bot.avatar.playerEntity!!.position = prevPos
			arrivalContinuation?.cancel(MoveError("NaN position"))
		}

		if (position != prevPos) {
			if (look != prevLook) {
				bot.sendPacket(ClientPlayerPositionRotationPacket(
					onGround,
					position.x,
					position.y,
					position.z,
					look.yawDegrees.toFloat(),
					look.pitchDegrees.toFloat()))
			} else {
				bot.sendPacket(ClientPlayerPositionPacket(
					onGround,
					position.x,
					position.y,
					position.z))
			}
		} else {
			if (look != prevLook) {
				bot.sendPacket(ClientPlayerRotationPacket(
					onGround,
					look.yawDegrees.toFloat(),
					look.pitchDegrees.toFloat()))
			} else {
				bot.sendPacket(ClientPlayerMovementPacket((onGround)))
			}
		}

		prevPos = position
		prevLook = look

		if (bumpedIntoWall != null) {
			// TODO stopSprinting()
			arrivalContinuation?.cancel(MoveError("Bumped into wall"))
		}

		bot.post(AvatarEvent.PosLookSent(position, look))
	}

	private fun onTeleportedByServer(event: AvatarEvent.TeleportedByServer) {
		if (event.packet is ServerVehicleMovePacket) {
			// in vehicle
			onGround = true
			velocity = Vec3d.origin
			return
		}
		if (event.packet !is ServerPlayerPositionRotationPacket) {
			Logger.getLogger("Physics").warning("TeleportedByServer via unexpected packet: ${event.packet}")
			return
		}

		// protocol requires these responses
		bot.sendPacket(ClientTeleportConfirmPacket(event.packet.teleportId))
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
			prevPos = position
			prevLook = look
		}

		// TODO if inside block by less than one pixel (1/16m), move up to surface
		// otherwise would glitch up and down in server-client disagreement

		reset()
	}
}

data class Collision(val box: Box, val face: Cardinal)
data class MoveAdjustResult(val endBox: Box, val collisions: Collection<Collision>)

fun calcMoveDest(moveVec: Vec3d, source: Box, obstacles: Collection<Box>): MoveAdjustResult {
	val sourceSize = source.size
	// grow obstacles by the source size to allow reducing source to a point
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
			val (intercept, facing) = obstacle.calculateIntercept(ray)
				?: continue // no intercept
			if ((endPosCurr - startPosCurr).lengthSquared()
				> (intercept - startPosCurr).lengthSquared()) {
				// this collision is closer
				endPosCurr = intercept
				closestCollision = Collision(obstacle, facing)
			}
		}

		if (closestCollision == null) break
		// shrink collision back to actual size (see above)
		collisions.add(closestCollision.copy(box = closestCollision.box.copy(
			min = closestCollision.box.min + sourceSize
		)))

		// prepare next iteration
		moveVecCurr -= endPosCurr - startPosCurr
		moveVecCurr = moveVecCurr.withAxis(closestCollision.face.axis, 0.0)
		startPosCurr = endPosCurr
		endPosCurr = startPosCurr + moveVecCurr // maximum movement
	}

	val endBox = source + (endPosCurr - source.min)
	return MoveAdjustResult(endBox, collisions)
}
