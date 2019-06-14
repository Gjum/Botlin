package com.github.gjum.minecraft.botlin.api

interface Behaviors : Service {
    fun provideBehavior(behavior: Behavior)
    val availableBehaviors: Collection<Behavior>
    fun getBehavior(name: String): Behavior?
}

interface Behavior {
    /** Unique identifier across all available behaviors. */
    val name: String

    val description: String

    /**
     * Creates [BehaviorInstance] for this behavior.
     */
    fun activate(avatar: Avatar): BehaviorInstance
}

/**
 * Tracks [avatar] related state of a behavior.
 */
abstract class BehaviorInstance(val avatar: Avatar) {
    abstract val name: String

    /** Remove all event hooks from [avatar]. */
    open fun deactivate() = Unit

    open fun doPhysicsTick() = Unit
}
