package com.github.gjum.minecraft.botlin

import com.github.gjum.minecraft.botlin.api.Behaviors
import com.github.gjum.minecraft.botlin.api.Module
import com.github.gjum.minecraft.botlin.api.Service
import com.github.gjum.minecraft.botlin.modules.ModulesLoader
import com.github.gjum.minecraft.botlin.modules.ServiceRegistry
import com.github.gjum.minecraft.botlin.modules.defaults.*
import com.github.gjum.minecraft.botlin.state.IdleBehavior
import com.github.gjum.minecraft.botlin.state.ReconnectBehavior
import java.io.File

object NewMain {
    @JvmStatic
    fun main(args: Array<String>) {
        // val loader = DirectoryModulesLoader(Module::class.java, File("modules"))
        val loader = StaticModulesLoader(createDefaultModules() + MainArgsModule(args))
        val serviceRegistry = ServiceRegistry(loader)
        serviceRegistry.consumeService(Behaviors::class.java) { behaviors ->
            behaviors ?: return@consumeService
            // TODO do this in DefaultBehaviorsModule
            behaviors.provideBehavior(IdleBehavior())
            behaviors.provideBehavior(ReconnectBehavior())
        }
        // everything else happens asynchronously inside the modules
    }
}

fun createDefaultModules(): Collection<Module> = listOf(
    AuthModule(),
    AvatarModule(),
    BehaviorsModule(),
    CliModule(),
    CommandModule(),
    EventLoggerModule(),
    RateLimitedConnectModule()
)

class StaticModulesLoader(private val modules: Collection<Module>) : ModulesLoader<Module> {
    override fun reload(modulesDir: File?): Collection<Module>? = modules
    override fun getAvailableModules(): Collection<Module> = modules
}

interface MainArgs : Service {
    fun getArgs(): Array<String>
}

class MainArgsModule(private val args: Array<String>) : Module() {
    override suspend fun initialize(serviceRegistry: ServiceRegistry, oldModule: Module?) {
        serviceRegistry.provideService(MainArgs::class.java, object : MainArgs {
            override fun getArgs() = args
        })
    }
}
