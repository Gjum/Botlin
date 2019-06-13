package com.github.gjum.minecraft.botlin.api

interface Behavior {
    /** Unique identifier across all available behaviors. */
    val name: String

    /** Hook up events to [avatar]. */
    fun activate(avatar: Avatar)

    /** Remove all event hooks from [avatar]. */
    fun deactivate(avatar: Avatar)

    fun doPhysicsTick(avatar: Avatar)
}
