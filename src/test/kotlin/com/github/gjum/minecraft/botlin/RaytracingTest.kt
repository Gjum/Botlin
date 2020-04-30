package com.github.gjum.minecraft.botlin

import com.github.gjum.minecraft.botlin.util.Ray
import com.github.gjum.minecraft.botlin.util.calculateIntercept
import com.github.gjum.minecraft.botlin.util.intersectionWithOrthoPlane
import com.github.gjum.minecraft.jmcdata.math.Axis
import com.github.gjum.minecraft.jmcdata.math.Box
import com.github.gjum.minecraft.jmcdata.math.Cardinal
import com.github.gjum.minecraft.jmcdata.math.Vec3d
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RayTracingTest {
	@Test
	fun intersectFromToWithOrthoPlaneTest() {
		for (axis in Axis.values()) {
			for (sign in listOf(-1, 1)) {
				assertEquals(Vec3d.origin.withAxis(axis, sign * .2), intersectionWithOrthoPlane(Ray(Vec3d.origin, Vec3d.origin.withAxis(axis, sign * .9)), sign * .2, axis))
			}
		}
	}

	@Test
	fun calculateInterceptTest() {
		val box = Box(Vec3d.unit * -.5, Vec3d.unit * .5)
		for (d1 in listOf(-.999, -.5, .0, .5, .999)) {
			for (d2 in listOf(-.999, -.5, .0, .5, .999)) {
				val result = box.calculateIntercept(Ray(
					Vec3d.origin,
					Vec3d.origin.copy(x = -1.0, y = d1, z = d2)))

				assertNotNull(result)
				result!!
				assertEquals(Cardinal.WEST, result.facing)
				assertEquals(-.5, result.intercept.x)
				if (d1 == .0) assertEquals(.0, result.intercept.y)
				if (d2 == .0) assertEquals(.0, result.intercept.z)
			}
		}
	}

	@Test
	fun `does not collide when hugging a face`() {
		val box = Box(Vec3d.origin, Vec3d.unit)
		val z = 0.0
		val values = listOf(-.999, -.5, .0, .5, .999)
		for (x1 in values) {
			for (y1 in values) {
				for (x2 in values) {
					for (y2 in values) {
						val result = box.calculateIntercept(Ray(
							Vec3d(x1, y1, z),
							Vec3d(x2, y2, z)))
						assertNull(result)
					}
				}
			}
		}
	}
}
