package com.github.gjum.minecraft.botlin.util

import com.github.gjum.minecraft.botlin.api.AvatarEvent
import com.github.gjum.minecraft.botlin.api.Entity
import com.github.gjum.minecraft.botlin.api.Vec3d
import com.github.gjum.minecraft.botlin.defaults.EventEmitterImpl
import io.mockk.confirmVerified
import io.mockk.spyk
import io.mockk.verifyOrder
import org.junit.Test
import kotlin.coroutines.EmptyCoroutineContext

class EventTestKotlin {
    @Test
    fun eventTest() {
        val ee = object : EventEmitterImpl<AvatarEvent>() {
            override val coroutineContext = EmptyCoroutineContext
        }

        val trackEventHandler: (String) -> Unit = spyk()

        // events get handled
        ee.onEach(AvatarEvent.Spawned::class.java) {
            it.run {
                trackEventHandler("AAA Spawned ${entity.position}")
            }
        }
        // multiple handlers same event
        ee.onEach(AvatarEvent.Spawned::class.java) {
            it.run {
                trackEventHandler("BBB Spawned ${entity.position}")
            }
        }
        // multiple handlers different events
        ee.onEach(AvatarEvent.TeleportedByServer::class.java) {
            it.run {
                trackEventHandler("PosChange $newPos")
            }
        }

        // events get emitted
        ee.emit(AvatarEvent.TeleportedByServer(Vec3d(1.0, 1.0, 1.0), Vec3d.origin, null))
        // events get emitted each time
        ee.emit(AvatarEvent.TeleportedByServer(Vec3d(2.0, 2.0, 2.0), Vec3d.origin, null))
        // different events get emitted
        ee.emit(AvatarEvent.Spawned(Entity(42).also {
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
