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
     * All services provided by each currently loaded module.
     */
    private val providerModules = mutableMapOf<Module, MutableCollection<Service>>()

    /**
     * All modules wanting to consume each service.
     */
    private val consumerHandlers = mutableMapOf<Class<out Service>, MutableCollection<ServiceChangeHandler<Any>>>()

    /**
     * All services consumed by each currently loaded module.
     */
    private val consumerModules = mutableMapOf<Module, MutableCollection<Class<out Service>>>()

    init {
        transition(emptyList(), modulesLoader.getAvailableModules())
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
     * Signal interest in consuming the [service].
     */
    fun <T : Service> handleServiceChange(module: Module, service: Class<T>, handler: ServiceChangeHandler<T>) {
        consumerModules.getOrPut(module, ::mkMutList)
            .add(service)

        @Suppress("UNCHECKED_CAST")
        val handlerAny = handler as ServiceChangeHandler<Any>

        consumerHandlers.getOrPut(service, ::mkMutList)
            .add(handlerAny)
    }

    fun <T : Service> provideService(module: Module, service: Class<T>, provider: T) {
        // mark the service as provided
        val prevProvider = providers.putIfAbsent(service, provider)
        if (prevProvider == null) { // service was not already provided by other module
            // mark this module to be providing this service
            providerModules.getOrPut(module, ::mkMutList)
                .add(provider)
            // notify consumers of this service that it is now available
            consumerHandlers[service]?.forEach { handler -> handler(provider) }
        }
    }

    /**
     * Performs upgrade after reload (and initial load).
     */
    private fun transition(oldModules: Collection<Module>, newModules: Collection<Module>) {
        val newModulesMap = newModules.map { it.name to it }.toMap()
        oldModules.forEach { it.teardown(newModulesMap[it.name]) }
        consumerHandlers.clear()
        providerModules.clear()
        consumerModules.clear()
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
