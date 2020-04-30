package com.github.gjum.minecraft.botlin.util

import com.github.gjum.minecraft.jmcdata.math.Axis
import com.github.gjum.minecraft.jmcdata.math.Box
import com.github.gjum.minecraft.jmcdata.math.Cardinal
import com.github.gjum.minecraft.jmcdata.math.Vec3d

data class Ray(val start: Vec3d, val delta: Vec3d)

data class RayTraceResult(val intercept: Vec3d, val facing: Cardinal)

fun Box.calculateIntercept(ray: Ray): RayTraceResult? {
	var intercept = collideWithOrthoPlane(ray, min.x, Axis.X)
	var facing = Cardinal.WEST

	var collisionVec = collideWithOrthoPlane(ray, max.x, Axis.X)
	if (collisionVec != null && (intercept == null ||
			(collisionVec - ray.start).lengthSquared()
			< (intercept - ray.start).lengthSquared())) {
		intercept = collisionVec
		facing = Cardinal.EAST
	}

	collisionVec = collideWithOrthoPlane(ray, min.y, Axis.Y)
	if (collisionVec != null && (intercept == null ||
			(collisionVec - ray.start).lengthSquared()
			< (intercept - ray.start).lengthSquared())) {
		intercept = collisionVec
		facing = Cardinal.DOWN
	}

	collisionVec = collideWithOrthoPlane(ray, max.y, Axis.Y)
	if (collisionVec != null && (intercept == null ||
			(collisionVec - ray.start).lengthSquared()
			< (intercept - ray.start).lengthSquared())) {
		intercept = collisionVec
		facing = Cardinal.UP
	}

	collisionVec = collideWithOrthoPlane(ray, min.z, Axis.Z)
	if (collisionVec != null && (intercept == null ||
			(collisionVec - ray.start).lengthSquared()
			< (intercept - ray.start).lengthSquared())) {
		intercept = collisionVec
		facing = Cardinal.NORTH
	}

	collisionVec = collideWithOrthoPlane(ray, max.z, Axis.Z)
	if (collisionVec != null && (intercept == null ||
			(collisionVec - ray.start).lengthSquared()
			< (intercept - ray.start).lengthSquared())) {
		intercept = collisionVec
		facing = Cardinal.SOUTH
	}

	if (intercept == null) return null
	return RayTraceResult(intercept, facing)
}

fun Box.collideWithOrthoPlane(ray: Ray, planeCoord: Double, axis: Axis): Vec3d? {
	val vecIntersect = intersectionWithOrthoPlane(ray, planeCoord, axis)
		?: return null
	if (!intersectsFace(vecIntersect, axis)) return null
	return vecIntersect
}

/**
 * Returns the intersection of [ray] with the plane
 * with [axis]-coordinate equal to [planeCoord],
 * or null if not possible (parallel).
 */
fun intersectionWithOrthoPlane(ray: Ray, planeCoord: Double, axis: Axis): Vec3d? {
	val deltaCoord = ray.delta.getAxis(axis)
	if (deltaCoord * deltaCoord < 0.0000001) {
		return null // ray parallel to plane
	}
	val startCoord = ray.start.getAxis(axis)
	val scalar = (planeCoord - startCoord) / deltaCoord
	if (scalar !in 0.0..1.0) {
		return null // plane too far behind/ahead of ray
	}
	return ray.start + ray.delta * scalar
}

fun Box.intersectsFace(vec: Vec3d, axis: Axis): Boolean {
	if (axis != Axis.X && !(min.x < vec.x && vec.x < max.x)) return false
	if (axis != Axis.Y && !(min.y < vec.y && vec.y < max.y)) return false
	if (axis != Axis.Z && !(min.z < vec.z && vec.z < max.z)) return false
	return true
}
