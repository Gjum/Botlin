package com.github.gjum.minecraft.botlin.api

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

typealias ServiceChangeHandler<T> = (provider: T?) -> Unit

/**
 * Keeps track of modules and services. Allows hot reloading modules in one directory.
 */
interface ServiceRegistry {
    /**
     * Signal interest in consuming the [service].
     * The [handler] will get called exactly once with the [service]'s provider.
     * If there is no provider available, [handler] gets called with `null`.
     */
    fun <T : Service> consumeService(service: Class<T>, handler: ServiceChangeHandler<T>)

    fun <T : Service> provideService(service: Class<T>, provider: T)
}

/**
 * Lookup the service provider for [service].
 * Returns null if no provider is registered.
 */
suspend inline fun <reified T : Service> ServiceRegistry.consumeService(service: Class<T>): T? {
    return suspendCancellableCoroutine { cont ->
        consumeService(service, cont::resume)
    }
}
