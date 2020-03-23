package com.github.gjum.minecraft.botlin.api

const val DRAG = 0.98 // y velocity multiplicator per tick when not on ground
const val GRAVITY = 0.08 // m/t²; subtracted from y velocity per tick when not on ground
const val JUMP_FORCE = 0.42 // m/t; applied to velocity when starting a jump
const val WALK_SPEED = 4.3 / 20
const val RUN_SPEED = 5.612 / 20
const val STEPPING_HEIGHT = .5
const val VERY_CLOSE = 0.00001 // consider arrived at target if squared distance is closer than this

val playerBox = Box(Vec3d(-.3, .0, -.3), Vec3d(.3, 1.8, .3))

typealias Route = Unit // TODO return route from move*() to allow better movement planning

open class PhysicsError(message: String) : Throwable(message)
class MoveError(message: String) : PhysicsError(message)
class JumpError(message: String) : PhysicsError(message)
