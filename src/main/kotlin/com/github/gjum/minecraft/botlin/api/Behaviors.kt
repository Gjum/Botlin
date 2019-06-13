package com.github.gjum.minecraft.botlin.api

interface Behaviors : Service {
    fun provideBehavior(behavior: Behavior)
    fun listBehaviors(): Collection<Behavior>
    fun getBehavior(name: String): Behavior?
}

interface Behavior {
    /** Unique identifier across all available behaviors. */
    val name: String

    /**
     * Creates [BehaviorInstance] for this behavior.
     */
    fun activate(avatar: Avatar): BehaviorInstance
}

/**
 * Tracks [avatar] related state of a behavior.
 */
abstract class BehaviorInstance(val avatar: Avatar) {
    /** Remove all event hooks from [avatar]. */
    open fun deactivate() = Unit

    open fun doPhysicsTick() = Unit
}
