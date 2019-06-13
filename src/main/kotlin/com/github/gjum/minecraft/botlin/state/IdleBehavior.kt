package com.github.gjum.minecraft.botlin.state

import com.github.gjum.minecraft.botlin.api.Avatar
import com.github.gjum.minecraft.botlin.api.Behavior
import com.github.gjum.minecraft.botlin.api.BehaviorInstance

class IdleBehavior : Behavior {
    override val name = "Idle"
    override fun activate(avatar: Avatar) = IdleBehaviorInstance(avatar)
}

class IdleBehaviorInstance(avatar: Avatar) : BehaviorInstance(avatar) {
    override fun doPhysicsTick() {
        // XXX fall once, upon hitting ground stop falling
    }
}
