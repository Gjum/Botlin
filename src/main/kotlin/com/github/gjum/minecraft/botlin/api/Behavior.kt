package com.github.gjum.minecraft.botlin.api

interface Behavior {
    /**
     * uniquely identify across all available behaviors
     */
    val name: String

    /**
     * hook up events
     */
    fun activate(avatar: Avatar)

    /**
     * remove all event hooks from avatar
     */
    fun deactivate()
}
