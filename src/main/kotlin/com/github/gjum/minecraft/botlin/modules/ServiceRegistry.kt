package com.github.gjum.minecraft.botlin.modules

import com.github.gjum.minecraft.botlin.api.CommandService
import com.github.gjum.minecraft.botlin.api.Module
import com.github.gjum.minecraft.botlin.api.Service
import com.github.gjum.minecraft.botlin.api.registerCommand
import java.io.File

typealias ServiceChangeHandler<T> = (provider: T?) -> Unit

/**
 * Keeps track of modules and services. Allows hot reloading modules in one directory.
 */
class ServiceRegistry(private val modulesLoader: ModulesLoader<Module>) {
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
        transition(emptyList(), modulesLoader.getAvailableModules())
    }

    /**
     * Signal interest in consuming the [service].
     * The [handler] will get called exactly once with the [service]'s provider.
     * If there is no provider available, [handler] gets called with `null`.
     */
    fun <T : Service> consumeService(service: Class<T>, handler: ServiceChangeHandler<T>) {
        @Suppress("UNCHECKED_CAST")
        val handlerAny = handler as ServiceChangeHandler<Any>

        consumerHandlers.getOrPut(service, ::mkMutList)
            .add(handlerAny)
    }

    fun <T : Service> provideService(service: Class<T>, provider: T) {
        // mark the service as provided
        val prevProvider = providers.putIfAbsent(service, provider)
        if (prevProvider == null) { // service was not already provided by other module
            // notify consumers of this service that it is now available
            consumerHandlers[service]?.forEach { handler -> handler(provider) }
        }
    }

    /**
     * Load all modules in [modulesDirectory], replacing all currently loaded modules.
     */
    fun reloadModules(modulesDirectory: File? = null) {
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
        oldModules.forEach { it.teardown(newModulesMap[it.name]) }
        consumerHandlers.clear()
        providers.clear()

        consumerHandlers.getOrPut(CommandService::class.java, ::mkMutList).add { commands ->
            if (commands !is CommandService) return@add
            commands.registerCommand(
                "reload",
                "reload",
                "Reload all modules."
            ) { command, context ->
                reloadModules()
            }
        }

        val oldModulesMap = oldModules.map { it.name to it }.toMap()
        // skip duplicate names by iterating newModulesMap instead of newModules
        newModulesMap.values.forEach { it.initialize(this, oldModulesMap[it.name]) }

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
