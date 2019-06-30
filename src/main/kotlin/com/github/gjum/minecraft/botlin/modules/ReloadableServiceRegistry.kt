package com.github.gjum.minecraft.botlin.modules

import com.github.gjum.minecraft.botlin.api.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * Lookup the service provider for [service].
 * Returns null if no provider is registered.
 */
suspend inline fun <reified T : Service> ServiceRegistry.consumeService(service: Class<T>): T? {
    return suspendCancellableCoroutine { cont ->
        consumeService(service, cont::resume)
    }
}

/**
 * Keeps track of modules and services. Allows hot reloading modules in one directory.
 */
class ReloadableServiceRegistry(
    private val modulesLoader: ModulesLoader<Module>
) : ServiceRegistry {
    /**
     * The provider of each service.
     */
    private val providers = mutableMapOf<Class<out Service>, Service>()

    /**
     * All modules wanting to consume each service.
     */
    private val consumerHandlers = mutableMapOf<Class<out Service>,
        MutableCollection<ServiceChangeHandler<Any>>>()

    init {
        val newModules = modulesLoader.getAvailableModules()
        transition(emptyList(), newModules)
    }

    override fun <T : Service> consumeService(service: Class<T>, handler: ServiceChangeHandler<T>) {
        @Suppress("UNCHECKED_CAST")
        val handlerAny = handler as ServiceChangeHandler<Any>

        consumerHandlers.getOrPut(service, ::mkMutList)
            .add(handlerAny)
    }

    override fun <T : Service> provideService(service: Class<T>, provider: T) {
        // mark the service as provided
        val prevProvider = providers.putIfAbsent(service, provider)
        if (prevProvider == null) { // service was not already provided by other module
            // notify consumers of this service that it is now available
            consumerHandlers[service]?.forEach { handler -> handler(provider) }
        }
    }

    fun reloadModules(modulesDirectory: File?) {
        val oldModules = modulesLoader.getAvailableModules()
        val newModules = modulesLoader.reload(modulesDirectory) ?: emptyList()
        transition(oldModules, newModules)
    }

    /**
     * Unloads all modules to end the program.
     */
    fun teardown() {
        val oldModules = modulesLoader.getAvailableModules()
        transition(oldModules, emptyList())
    }

    /**
     * Performs upgrade after reload (and initial load/final unload).
     */
    private fun transition(oldModules: Collection<Module>, newModules: Collection<Module>) {
        val newModulesMap = newModules.map { it.name to it }.toMap()
        oldModules.forEach { it.deactivate() }
        consumerHandlers.clear()
        providers.clear()

        // skip duplicate names by iterating newModulesMap instead of newModules
        newModulesMap.values.forEach {
            runBlocking {
                it.activate(this@ReloadableServiceRegistry)
            }
        }

        consumeService(CommandService::class.java) { commands ->
            commands?.registerCommand("reload", "reload", "Reload all modules."
            ) { command, context ->
                reloadModules(null)
            }
        }

        // send null for unavailable wanted services
        consumerHandlers.forEach { service, handlers ->
            val provider = providers[service]
            if (provider == null) {
                handlers.forEach { it(provider) }
            }
        }
    }

    private fun <T> mkMutList() = mutableListOf<T>()
}
