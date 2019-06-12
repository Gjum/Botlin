package com.github.gjum.minecraft.botlin.util

import com.github.gjum.minecraft.botlin.api.AvatarEvents
import com.github.gjum.minecraft.botlin.state.Entity
import io.mockk.confirmVerified
import io.mockk.spyk
import io.mockk.verifyOrder
import org.junit.Test

class EventTestKotlin {
    @Test
    fun eventTest() {
        val ee = EventEmitterImpl<AvatarEvents>()

        val foo: (String) -> Unit = spyk()

        // events get handled
        ee.on(AvatarEvents.Spawned) { entity ->
            foo("AAA Spawned ${entity.position}")
        }
        // multiple handlers same event
        ee.on(AvatarEvents.Spawned) { entity ->
            foo("BBB Spawned ${entity.position}")
        }
        // multiple handlers different events
        ee.on(AvatarEvents.PositionChanged) { newPos, oldPos, reason ->
            foo("PosChange $newPos")
        }

        // events get emitted
        ee.emit(AvatarEvents.PositionChanged) {
            it.invoke(Vec3d(1.0, 1.0, 1.0), Vec3d.origin, null)
        }
        // events get emitted each time
        ee.emit(AvatarEvents.PositionChanged) {
            it.invoke(Vec3d(2.0, 2.0, 2.0), Vec3d.origin, null)
        }
        // different events get emitted
        ee.emit(AvatarEvents.Spawned) {
            it.invoke(Entity(42).also { e ->
                e.position = Vec3d(3.0, 3.0, 3.0)
            })
        }

        verifyOrder {
            foo("PosChange [1.0 1.0 1.0]")
            foo("PosChange [2.0 2.0 2.0]")
            foo("AAA Spawned [3.0 3.0 3.0]")
            foo("BBB Spawned [3.0 3.0 3.0]")
        }
        confirmVerified(foo)
    }
}
