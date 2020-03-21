package com.github.gjum.minecraft.botlin.api

import kotlin.math.*

val Double.floor: Int get() = floor(this).toInt()

fun Double.round(places: Int): Double {
	val zeroes = 10.0.pow(places)
	return (this * zeroes).roundToInt() / zeroes
}

fun Int.euclideanDiv(denominator: Int): Int =
	(this / denominator).let { if (this < 0) it - 1 else it }

fun Int.euclideanMod(denominator: Int): Int =
	(this % denominator).let { if (this < 0) it + denominator else it }

fun Double.euclideanMod(denominator: Double): Double =
	(this % denominator).let { if (this < 0) it + denominator else it }

val Int.div16 get() = this shr 4
val Int.mod16 get() = this and 0xf

data class ChunkPos(val x: Int, val z: Int) {
	companion object {
		fun fromBlock(x: Int, z: Int) = ChunkPos(x.div16, z.div16)
	}
}

enum class Axis { X, Y, Z }

enum class Cardinal(val axis: Axis, val sign: Int) {
	WEST(Axis.X, -1),
	EAST(Axis.X, 1),
	DOWN(Axis.Y, -1),
	UP(Axis.Y, 1),
	NORTH(Axis.Z, -1),
	SOUTH(Axis.Z, 1);

	val asVec3i get() = Vec3i.origin.withAxis(axis, sign)
}

// TODO separate Pos3d type, to detect math errors at compile time

data class Vec3d(val x: Double, val y: Double, val z: Double) {
	override fun toString() = "[${x.round(2)} ${y.round(2)} ${z.round(2)}]"

	operator fun times(scalar: Double) = Vec3d(x * scalar, y * scalar, z * scalar)
	operator fun div(scalar: Double) = Vec3d(x / scalar, y / scalar, z / scalar)
	operator fun plus(other: Vec3d) = other.let { Vec3d(x + it.x, y + it.y, z + it.z) }
	operator fun unaryMinus() = Vec3d(-x, -y, -z)
	operator fun minus(other: Vec3d) = other.let { Vec3d(x - it.x, y - it.y, z - it.z) }

	fun floored() = Vec3i(x.floor, y.floor, z.floor)
	fun rounded() = Vec3i(x.roundToInt(), y.roundToInt(), z.roundToInt())
	fun normed() = Vec3d(x, y, z) / length()
	fun length() = sqrt(lengthSquared())
	fun lengthSquared() = x * x + y * y + z * z

	fun getAxis(axis: Axis): Double = when (axis) {
		Axis.X -> x
		Axis.Y -> y
		Axis.Z -> z
	}

	fun withAxis(axis: Axis, value: Double): Vec3d = when (axis) {
		Axis.X -> Vec3d(value, y, z)
		Axis.Y -> Vec3d(x, value, z)
		Axis.Z -> Vec3d(x, y, value)
	}

	companion object {
		val origin = Vec3d(0.0, 0.0, 0.0)
		val unit = Vec3d(1.0, 1.0, 1.0)
	}
}

data class Vec3i(val x: Int, val y: Int, val z: Int) {
	override fun toString() = "[$x $y $z]"

	operator fun times(scalar: Int) = Vec3i(x * scalar, y * scalar, z * scalar)
	operator fun times(scalar: Double) = Vec3d(x * scalar, y * scalar, z * scalar)
	operator fun div(scalar: Double) = Vec3d(x / scalar, y / scalar, z / scalar)
	operator fun plus(other: Vec3i) = other.let { Vec3i(x + it.x, y + it.y, z + it.z) }
	operator fun unaryMinus() = Vec3i(-x, -y, -z)
	operator fun minus(other: Vec3i) = other.let { Vec3i(x - it.x, y - it.y, z - it.z) }

	val asVec3d get() = this * 1.0
	fun normed() = Vec3i(x, y, z) / length()
	fun length() = sqrt(lengthSquared().toDouble())
	fun lengthSquared() = x * x + y * y + z * z

	fun getAxis(axis: Axis): Int = when (axis) {
		Axis.X -> x
		Axis.Y -> y
		Axis.Z -> z
	}

	fun withAxis(axis: Axis, value: Int): Vec3i = when (axis) {
		Axis.X -> Vec3i(value, y, z)
		Axis.Y -> Vec3i(x, value, z)
		Axis.Z -> Vec3i(x, y, value)
	}

	companion object {
		val origin = Vec3i(0, 0, 0)
		val unit = Vec3i(1, 1, 1)
	}
}

typealias Radians = Double
typealias Degrees = Double

const val TAU = 2.0 * PI

fun Double.degFromRad(): Degrees = this * 360.0 / TAU
fun Double.radFromDeg(): Radians = this * TAU / 360.0
fun Float.degFromRad(): Degrees = this * 360.0 / TAU
fun Float.radFromDeg(): Radians = this * TAU / 360.0

private val compass8Names = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

fun Radians.compass8(): String {
	val yawEights = this * 8 / (2 * PI)
	val index = 4 + yawEights.roundToInt()
	return compass8Names[(index + 8).euclideanMod(8)]
}

/**
 * [yaw] and [pitch] are Euler angles measured in radians.
 * yaw = pitch = 0 is looking straight south.
 * yaw = -pi/2 is looking straight east.
 * pitch = -pi/2 is looking straight up.
 */
data class Look(val yaw: Radians, val pitch: Radians) {
	override fun toString() = "(${yawDegrees.roundToInt() % 360}°${yaw.compass8()}, ${pitchDegrees.roundToInt() % 360}°)"

	operator fun plus(other: Look) = other.let { Look(yaw + it.yaw, pitch + it.pitch) }
	operator fun unaryMinus() = Look(-yaw, -pitch)
	operator fun minus(other: Look) = this + (-other)

	fun turnToVec3(delta: Vec3d): Look {
		val groundDistance = delta.run { sqrt(x * x + z * z) }
		val pitchNew = -atan2(delta.y, groundDistance)
		val yawNew = if (-PI / 2 < pitchNew && pitchNew < PI / 2) {
			PI - atan2(-delta.x, -delta.z)
		} else yaw // keep current yaw and look straight up/down
		return Look(yawNew, pitchNew)
	}

	val asVec3d by lazy {
		Vec3d(
			-cos(pitch) * sin(yaw),
			-sin(pitch),
			cos(pitch) * cos(yaw))
	}

	val yawDegrees: Degrees by lazy { yaw.degFromRad() }
	val pitchDegrees: Degrees by lazy { pitch.degFromRad() }

	companion object {
		val origin = Look(0.0, 0.0)

		/**
		 * Use [turnToVec3] if possible, it uses the previous yaw when looking straight up/down.
		 */
		fun fromVec3(delta: Vec3d) = origin.turnToVec3(delta)

		fun fromDegrees(yaw: Float, pitch: Float): Look {
			return Look(yaw.toDouble().radFromDeg(), pitch.toDouble().radFromDeg())
		}
	}
}

data class Box(val min: Vec3d, val max: Vec3d) {
	operator fun plus(vec: Vec3d) = Box(min + vec, max + vec)
	operator fun minus(vec: Vec3d) = Box(min - vec, max - vec)

	val center by lazy { (min + max) / 2.0 }
	val size by lazy { max - min }

	val isEmpty by lazy {
		min.x <= 0 || min.y <= 0 || min.z <= 0
			|| max.x <= 0 || max.y <= 0 || max.z <= 0
	}

	/**
	 * result.min._ > 0 means [other] intrudes from the negative direction from this,
	 * result.max._ > 0 means [other] intrudes from the positive direction from this.
	 */
	fun intersection(other: Box) = Box(other.max - this.min, this.max - other.min)

	fun intersects(other: Box) = !intersection(other).isEmpty
}

data class Shape(val boxes: Collection<Box>) {
	val outerBox: Box? by lazy {
		boxes.singleOrNull()
			?: if (boxes.isEmpty()) null else Box(
				Vec3d(
					boxes.map { it.min.x }.min()!!,
					boxes.map { it.min.y }.min()!!,
					boxes.map { it.min.z }.min()!!),
				Vec3d(
					boxes.map { it.max.x }.max()!!,
					boxes.map { it.max.y }.max()!!,
					boxes.map { it.max.z }.max()!!)
			)
	}

	companion object {
		val EMPTY = Shape(emptyList())
		val SOLID = Shape(listOf(Box(Vec3d.origin, Vec3d.unit)))
	}
}
