package com.github.gjum.minecraft.botlin.state

import com.github.gjum.minecraft.botlin.api.Avatar
import com.github.gjum.minecraft.botlin.api.AvatarEvents
import com.github.gjum.minecraft.botlin.api.Behavior
import com.github.gjum.minecraft.botlin.api.BehaviorInstance
import com.github.gjum.minecraft.botlin.util.Vec3d
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerVehicleMovePacket
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerPositionRotationPacket

private const val behaviorName = "Idle"

class IdleBehavior : Behavior {
    override val name = behaviorName
    override val description = "Does basic physics (falling)."
    override fun activate(avatar: Avatar) = IdleBehaviorInstance(avatar)
}

open class IdleBehaviorInstance(avatar: Avatar) : BehaviorInstance(avatar) {
    override val name = behaviorName

    private var isFalling = true
    private var prevPos: Vec3d? = null

    init {
        avatar.on(AvatarEvents.TeleportByServer::class.java, ::onTeleportByServer)
    }

    override fun deactivate() {
        avatar.removeEventHandler(AvatarEvents.TeleportByServer::class.java, ::onTeleportByServer)
    }

    private fun onTeleportByServer(event: AvatarEvents.TeleportByServer) {
        if (event.reason is ServerVehicleMovePacket) {
            isFalling = false // in vehicle
        }
        if (event.reason is ServerPlayerPositionRotationPacket) {
            val isLanded = event.newPos == prevPos
            isFalling = !isLanded
        }
    }

    override fun doPhysicsTick() {
        if (isFalling) {
            prevPos = avatar.position!!
            val newPos = prevPos!! - Vec3d(0.0, 1.0 / 8, 0.0)
            avatar.entity!!.position = newPos
        }
    }
}
