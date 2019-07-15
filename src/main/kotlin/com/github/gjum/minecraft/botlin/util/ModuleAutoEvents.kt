package com.github.gjum.minecraft.botlin.util

import com.github.gjum.minecraft.botlin.api.*

/**
 * [Module] that automatically unregisters [AvatarEvents] during [deactivate]
 * if they were registered with [onEach].
 */
abstract class ModuleAutoEvents : Module() {
	protected val eventUnregisters = mutableListOf<Unregister>()

	protected var serviceRegistry: ServiceRegistry? = null
	private var internalAvatar: Avatar? = null // conveniently access avatar without !!
	protected val avatar get() = internalAvatar!!

	override suspend fun activate(serviceRegistry: ServiceRegistry) {
		super.activate(serviceRegistry)
		this.serviceRegistry = serviceRegistry
		internalAvatar = serviceRegistry.consumeService(Avatar::class.java)!!
	}

	override fun deactivate() {
		eventUnregisters.forEach { it() }
		internalAvatar = null
		serviceRegistry = null
	}

	/**
	 * Use this to register [avatar] events during init{}.
	 * They will automatically be unregistered in [deactivate].
	 */
	protected fun <T : AvatarEvent> onEach(event: EventClass<T>, handler: EventHandler<T>) {
		eventUnregisters.add(avatar.onEach(event, handler))
	}
}
