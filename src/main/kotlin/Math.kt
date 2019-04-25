package com.github.gjum.minecraft.botlin

import kotlin.math.*

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

data class Vec3d(var x: Double, var y: Double, var z: Double) {
    override fun toString() = "[${x.round(1)} ${y.round(1)} ${z.round(1)}]"

    operator fun times(scalar: Double) = Vec3d(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Double) = Vec3d(x / scalar, y / scalar, z / scalar)
    operator fun plus(other: Vec3d) = other.let { Vec3d(x + it.x, y + it.y, z + it.z) }
    operator fun unaryMinus() = Vec3d(-x, -y, -z)
    operator fun minus(other: Vec3d) = other.let { Vec3d(x - it.x, y - it.y, z - it.z) }
    operator fun minusAssign(other: Vec3d) = plusAssign(-other)
    operator fun plusAssign(d: Vec3d) {
        x += d.x
        y += d.y
        z += d.z
    }

    fun assign(x: Double, y: Double, z: Double) {
        this.x = x
        this.y = y
        this.z = z
    }

    fun assign(other: Vec3d) = other.let { this@Vec3d.assign(x, y, z) }

    fun floored() = Vec3i(floor(x).toInt(), floor(y).toInt(), floor(z).toInt())
    fun rounded() = Vec3i(x.roundToInt(), y.roundToInt(), z.roundToInt())
    fun normed() = Vec3d(x, y, z) / length()
    fun length() = sqrt(lengthSquared())
    fun lengthSquared() = x * x + y * y + z * z

    companion object {
        val origin = Vec3d(0.0, 0.0, 0.0)
    }
}

data class Vec3i(var x: Int, var y: Int, var z: Int) {
    override fun toString() = "[$x $y $z]"

    operator fun times(scalar: Int) = Vec3i(x * scalar, y * scalar, z * scalar)
    operator fun times(scalar: Double) = Vec3d(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Double) = Vec3d(x / scalar, y / scalar, z / scalar)
    operator fun plus(other: Vec3i) = other.let { Vec3i(x + it.x, y + it.y, z + it.z) }
    operator fun unaryMinus() = Vec3i(-x, -y, -z)
    operator fun minus(other: Vec3i) = other.let { Vec3i(x - it.x, y - it.y, z - it.z) }
    operator fun minusAssign(other: Vec3i) = plusAssign(-other)
    operator fun plusAssign(d: Vec3i) {
        x += d.x
        y += d.y
        z += d.z
    }

    fun assign(x: Int, y: Int, z: Int) {
        this.x = x
        this.y = y
        this.z = z
    }

    fun assign(other: Vec3i) = other.let { this@Vec3i.assign(x, y, z) }

    fun asVec3d() = this * 1.0
    fun normed() = Vec3i(x, y, z) / length()
    fun length() = sqrt(lengthSquared().toDouble())
    fun lengthSquared() = x * x + y * y + z * z

    companion object {
        val origin = Vec3i(0, 0, 0)
    }
}

typealias Radians = Double
typealias Degrees = Double
typealias AngleByte = Byte

fun Float.toRadians() = toDouble()
fun Float.toDegrees() = toDouble()

/**
 * yaw and pitch are Euler angles measured in radians.
 * yaw = pitch = 0 is looking straight south.
 * yaw = -pi/2 is looking straight east.
 * pitch = -pi/2 is looking straight up.
 */
data class Look(var yaw: Radians, var pitch: Radians) {
    override fun toString() = "(${yawDegrees().roundToInt() % 360}deg, ${pitchDegrees().roundToInt() % 360}deg)"

    operator fun plus(other: Look) = other.let { Look(yaw + it.yaw, pitch + it.pitch) }
    operator fun unaryMinus() = Look(-yaw, -pitch)
    operator fun minus(other: Look) = this + (-other)
    operator fun minusAssign(other: Look) = plusAssign(-other)
    operator fun plusAssign(d: Look) {
        yaw += d.yaw
        pitch += d.pitch
    }

    fun assign(yaw: Radians, pitch: Radians): Look {
        this.yaw = yaw
        this.pitch = pitch
        return this
    }

    fun assign(other: Look): Look = other.run { this@Look.assign(yaw, pitch) }

    fun turnToVec3(delta: Vec3d) = Look(yaw, pitch).also { it.setFromVec3(delta) }

    fun setFromVec3(delta: Vec3d) {
        val groundDistance = delta.run { sqrt(x * x + z * z) }
        pitch = -atan2(delta.y, groundDistance)
        if (-PI / 2 < pitch && pitch < PI / 2) {
            yaw = PI - atan2(-delta.x, -delta.z)
        } // else: keep current yaw and look straight up/down
    }

    fun toVec3() = Vec3d(-cos(pitch) * sin(yaw), -sin(pitch), cos(pitch) * cos(yaw))

    /*
    clientbound vehicle_move: f32
    clientbound position: f32
    serverbound position_look: f32
    serverbound look: f32
    serverbound vehicle_move: f32
     */

    fun yawDegrees(): Degrees = degFromRad(yaw)
    fun pitchDegrees(): Degrees = degFromRad(pitch)

    /*
    clientbound spawn_entity: i8
    clientbound spawn_entity_living: i8
    clientbound named_entity_spawn: i8
    clientbound entity_move_look: i8
    clientbound entity_look: i8
    clientbound entity_head_rotation: i8
    clientbound entity_teleport: i8
    */

    fun yawByte(): AngleByte = byteFromRad(yaw)
    fun pitchByte(): AngleByte = byteFromRad(pitch)

    companion object {
        val origin = Look(0.0, 0.0)

        /**
         * Use [Look.turnToVec3] if possible, it uses the previous pitch when looking straight up/down.
         */
        fun fromVec3(delta: Vec3d) = origin.turnToVec3(delta)

        fun fromDegrees(yaw: Float, pitch: Float): Look {
            return Look(radFromDeg(yaw.toDouble()), radFromDeg(pitch.toDouble()))
        }

        fun radFromByte(angle: Byte): Radians = angle * PI / 128.0
        fun byteFromRad(angle: Radians): AngleByte = (angle * 128.0 / PI).toByte()
        fun radFromDeg(angle: Degrees): Radians = angle * PI / 180.0
        fun degFromRad(angle: Radians): Degrees = angle * 180.0 / PI
    }
}
