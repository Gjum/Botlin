package com.github.gjum.minecraft.botlin.api

import com.github.gjum.minecraft.botlin.modules.ServiceRegistry

abstract class Module {
    /**
     * Unique identifier during runtime - only one module
     * of any given name can be loaded at any time.
     */
    open val name: String = this.javaClass.name

    open val description = ""

    /**
     * Activate the module in the context of [serviceRegistry].
     * This is only called up to once for any Module instance.
     *
     * When hot reloading and a module of the same name was loaded before the reload,
     * [oldModule] is set to the old instance to allow handover of state.
     * [teardown] will already have been called on [oldModule].
     *
     * Typical activities include [ServiceRegistry.consumeService]
     * and [ServiceRegistry.provideService].
     *
     * When providing a service, any related state should not be taken from [oldModule],
     * because other modules will be prompted to contact this new instance.
     */
    open suspend fun initialize(serviceRegistry: ServiceRegistry, oldModule: Module? = null) = Unit

    /**
     * Called when this module will be removed, e.g., after error or hot reload.
     * If hot reloading, [newModule] is set to the successor module; null otherwise.
     * [initialize] will not have been called yet on [newModule].
     */
    open fun teardown(newModule: Module?) = Unit
}
