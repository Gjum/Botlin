package com.github.gjum.minecraft.botlin.state

import com.github.gjum.minecraft.botlin.api.Avatar
import com.github.gjum.minecraft.botlin.api.Behavior

class IdleBehavior : Behavior {
    override val name = "Idle"

    override fun activate(avatar: Avatar) = Unit

    override fun deactivate(avatar: Avatar) = Unit

    override fun doPhysicsTick(avatar: Avatar) {
        // XXX fall once, upon hitting ground stop falling
    }
}
