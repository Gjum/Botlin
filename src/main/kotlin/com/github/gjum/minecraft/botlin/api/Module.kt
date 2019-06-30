package com.github.gjum.minecraft.botlin.api

abstract class Module {
	/**
	 * Unique identifier during runtime - only one module
	 * of any given name can be loaded at any time.
	 */
	open val name: String = this.javaClass.name

	open val description = ""

	/**
	 * Activate the module in the context of [serviceRegistry].
	 *
	 * Typical activities include [ServiceRegistry.consumeService]
	 * and [ServiceRegistry.provideService].
	 */
	open suspend fun activate(serviceRegistry: ServiceRegistry) = Unit

	/**
	 * Deactivate the module and its functionality.
	 * Must remove any references to this instance that were created
	 * (e.g., when registering with other modules).
	 *
	 * May throw [NotImplementedError] if this module does not support deactivation.
	 */
	open fun deactivate() {
		throw NotImplementedError("Deactivation not supported for $name")
	}
}
