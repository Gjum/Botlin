package com.github.gjum.minecraft.botlin.util

import com.github.gjum.minecraft.botlin.api.AvatarEvent
import com.github.gjum.minecraft.botlin.api.AvatarEvents
import com.github.gjum.minecraft.botlin.defaults.EventEmitterImpl
import com.github.gjum.minecraft.botlin.api.Entity
import com.github.gjum.minecraft.botlin.api.Vec3d
import io.mockk.confirmVerified
import io.mockk.spyk
import io.mockk.verifyOrder
import org.junit.Test

class EventTestKotlin {
    @Test
    fun eventTest() {
        val ee = EventEmitterImpl<AvatarEvent>()

        val trackEventHandler: (String) -> Unit = spyk()

        // events get handled
        ee.onEach(AvatarEvents.Spawned::class.java) {
            it.run {
                trackEventHandler("AAA Spawned ${entity.position}")
            }
        }
        // multiple handlers same event
        ee.onEach(AvatarEvents.Spawned::class.java) {
            it.run {
                trackEventHandler("BBB Spawned ${entity.position}")
            }
        }
        // multiple handlers different events
        ee.onEach(AvatarEvents.TeleportedByServer::class.java) {
            it.run {
                trackEventHandler("PosChange $newPos")
            }
        }

        // events get emitted
        ee.emit(AvatarEvents.TeleportedByServer(Vec3d(1.0, 1.0, 1.0), Vec3d.origin, null))
        // events get emitted each time
        ee.emit(AvatarEvents.TeleportedByServer(Vec3d(2.0, 2.0, 2.0), Vec3d.origin, null))
        // different events get emitted
        ee.emit(AvatarEvents.Spawned(Entity(42).also {
            it.position = Vec3d(3.0, 3.0, 3.0)
        }))

        verifyOrder {
            trackEventHandler("PosChange [1.0 1.0 1.0]")
            trackEventHandler("PosChange [2.0 2.0 2.0]")
            trackEventHandler("AAA Spawned [3.0 3.0 3.0]")
            trackEventHandler("BBB Spawned [3.0 3.0 3.0]")
        }
        confirmVerified(trackEventHandler)
    }
}
