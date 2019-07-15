package com.github.gjum.minecraft.botlin.modules

import com.github.gjum.minecraft.botlin.api.Module
import com.github.gjum.minecraft.botlin.api.Service
import com.github.gjum.minecraft.botlin.api.ServiceChangeHandler
import com.github.gjum.minecraft.botlin.api.ServiceRegistry
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Keeps track of modules and services. Allows hot reloading modules in one directory.
 */
class ReloadableServiceRegistry(
    private val modulesLoader: ModulesLoader<Module>
) : ServiceRegistry {
    private val logger = Logger.getLogger(this::class.java.name)

    /**
     * Currently active modules.
     */
    private val modules = mutableMapOf<String, Module>()

    /**
     * The provider of each service.
     */
    private val providers = mutableMapOf<Class<out Service>, Service>()

    /**
     * All modules wanting to consume each service.
     */
    private val consumerHandlers = mutableMapOf<Class<out Service>,
        MutableCollection<ServiceChangeHandler<Any>>>()

    override fun <T : Service> consumeService(service: Class<T>, handler: ServiceChangeHandler<T>) {
        val knownProvider = providers[service]
        if (knownProvider != null) {
            @Suppress("UNCHECKED_CAST")
            val tProvider = knownProvider as T?
            handler(tProvider)
            return
        }

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

    suspend fun teardown() {
        transition(emptyList())
    }

    suspend fun reloadModules(modulesDirectory: File?) {
        val newModules = modulesLoader.reload(modulesDirectory) ?: emptyList()
        transition(newModules)
    }

    /**
     * Performs upgrade after reload (and initial load/final unload).
     */
    private suspend fun transition(newModules: Collection<Module>) {
        val numOldModules = modules.size
        modules.values.forEach {
            logger.fine("Deactivating module ${it.name}")
            it.deactivate()
            modules.remove(it.name)
        }
        consumerHandlers.clear()
        providers.clear()
        logger.fine("Done deactivating $numOldModules modules")

        val newModulesMap = newModules.map { it.name to it }.toMap()
        coroutineScope {
            // skip duplicate names by iterating newModulesMap instead of newModules
            newModulesMap.values.forEach {
                logger.fine("Activating module ${it.name}")
                launch {
                    try {
                        it.activate(this@ReloadableServiceRegistry)
                        logger.fine("Done activating module ${it.name}")
                    } catch (e: Exception) {
                        logger.log(Level.SEVERE, "Failed activating module ${it.name}: $e", e)
                        return@launch
                    }
                    modules[it.name] = it
                }
            }
            logger.fine("Done kicking off modules activation")
        }
        logger.fine("Done activating ${newModulesMap.size} modules")

        // send null for unavailable wanted services
        consumerHandlers.forEach { (service, handlers) ->
            val provider = providers[service]
            if (provider == null) {
                handlers.forEach { it(provider) }
            }
        }
    }

    private fun <T> mkMutList() = mutableListOf<T>()
}
