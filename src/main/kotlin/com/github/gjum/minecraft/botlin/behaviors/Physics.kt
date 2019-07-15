package com.github.gjum.minecraft.botlin.behaviors

import com.github.gjum.minecraft.botlin.api.*

typealias Route = Unit // TODO return route from move*() to allow better movement planning

class MoveError

interface PhysicsService : Service {
	suspend fun moveStraightTo(destination: Vec3d): Result<Route, MoveError>
	suspend fun jump()
}

suspend fun Avatar.moveStraightTo(destination: Vec3d): Result<Route, MoveError> {
	val physics = serviceRegistry.consumeService(PhysicsService::class.java)!!
	return physics.moveStraightTo(destination)
}

suspend fun Avatar.jump() {
	val physics = serviceRegistry.consumeService(PhysicsService::class.java)!!
	return physics.jump()
}
