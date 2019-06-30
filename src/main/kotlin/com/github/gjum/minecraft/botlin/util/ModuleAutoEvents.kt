package com.github.gjum.minecraft.botlin.util

import com.github.gjum.minecraft.botlin.api.*

/**
 * [Module] that automatically unregisters events during [deactivate]
 * if they were registered with [onEach].
 */
abstract class ModuleAutoEvents : Module() {
	protected val eventUnregisters = mutableListOf<Unregister>()

	protected var avatar: Avatar? = null
	protected var serviceRegistry: ServiceRegistry? = null

	override suspend fun activate(serviceRegistry: ServiceRegistry, avatar: Avatar) {
		super.activate(serviceRegistry, avatar)
		this.serviceRegistry = serviceRegistry
		this.avatar = avatar
	}

	override fun deactivate() {
		eventUnregisters.forEach { it() }
		avatar = null
		serviceRegistry = null
	}

	/**
	 * Use this to register [avatar] events during init{}.
	 * They will automatically be unregistered in [deactivate].
	 */
	protected fun <T : AvatarEvent> onEach(event: EventClass<T>, handler: EventHandler<T>) {
		eventUnregisters.add(avatar!!.onEach(event, handler))
	}
}
