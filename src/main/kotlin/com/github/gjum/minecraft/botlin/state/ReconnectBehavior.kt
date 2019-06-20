package com.github.gjum.minecraft.botlin.state

import com.github.gjum.minecraft.botlin.api.Avatar
import com.github.gjum.minecraft.botlin.api.AvatarEvents
import com.github.gjum.minecraft.botlin.api.Behavior

private const val behaviorName = "Reconnect"

class ReconnectBehavior : Behavior {
    override val name = behaviorName
    override val description = "Does auto-reconnection and basic physics (falling)."
    override fun activate(avatar: Avatar) = IdleBehaviorInstance(avatar)
}

/**
 * Inherits [doPhysicsTick] from [IdleBehaviorInstance].
 */
open class ReconnectBehaviorInstance(avatar: Avatar) : IdleBehaviorInstance(avatar) {
    override val name = behaviorName

    init {
        avatar.on(AvatarEvents.Disconnected::class.java, ::onDisconnected)
    }

    private fun onDisconnected(event: AvatarEvents.Disconnected) {
        // XXX auto reconnect
    }

    override fun deactivate() {
        avatar.removeEventHandler(AvatarEvents.Disconnected::class.java, ::onDisconnected)
    }
}
