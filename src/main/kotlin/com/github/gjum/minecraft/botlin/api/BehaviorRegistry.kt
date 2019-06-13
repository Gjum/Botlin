package com.github.gjum.minecraft.botlin.api

interface BehaviorRegistry : Service {
    fun provideBehavior(behavior: Behavior)
    fun listBehaviors(): Collection<Behavior>
    fun getBehavior(name: String): Behavior?
}
