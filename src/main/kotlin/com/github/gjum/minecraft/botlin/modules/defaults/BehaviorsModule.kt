package com.github.gjum.minecraft.botlin.modules.defaults

import com.github.gjum.minecraft.botlin.api.*
import com.github.gjum.minecraft.botlin.modules.ServiceRegistry
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class BehaviorsModule : Module() {
    private val registry = BehaviorsProvider()

    override suspend fun initialize(serviceRegistry: ServiceRegistry, oldModule: Module?) {
        serviceRegistry.provideService(Behaviors::class.java, registry)

        val avatars = serviceRegistry.consumeService(Avatars::class.java) ?: return

        val commands = serviceRegistry.consumeService(CommandService::class.java)
        commands?.registerCommand("behave",
            "behave <behavior> [server=previous] [username=default]",
            "Apply the behavior to that account on that server."
        ) { cmdLine, context ->
            val split = cmdLine.split(" +".toRegex())
            val behaviorName = split[1]
            val (servers, users) = split.partition { '.' in it || ':' in it }
            val serverAddress = servers.getOrElse(0) { TODO("previous server") }
            val username = users.getOrElse(0) { TODO("default username") }
            val behavior = registry.getBehavior(behaviorName) ?: return@registerCommand
            GlobalScope.launch {
                val avatar = avatars.getAvatar(username, serverAddress)
                avatar.useBehavior(behavior)
            }
        }

        launchDefaultBehavior(serviceRegistry)
    }

    /**
     * If mcHost and behavior properties are set
     * and Authentication service is available,
     * automatically applies the behavior to the default account on mcHost.
     */
    private suspend fun launchDefaultBehavior(serviceRegistry: ServiceRegistry) {
        val auth = serviceRegistry.consumeService(Authentication::class.java) ?: return
        val avatars = serviceRegistry.consumeService(Avatars::class.java) ?: return
        val hostProp = System.getProperty("mcHost") ?: return
        val behaviorProp = System.getProperty("behavior") ?: return
        val behavior = registry.getBehavior(behaviorProp) ?: return
        // getting defaultAccount and avatar may take a while
        // fork those into the background so initialize can finish
        GlobalScope.launch {
            val username = auth.defaultAccount ?: return@launch
            val avatar = avatars.getAvatar(username, hostProp)
            avatar.useBehavior(behavior)
        }
    }
}

class BehaviorsProvider : Behaviors {
    private val behaviors = mutableMapOf<String, Behavior>()

    override fun provideBehavior(behavior: Behavior) {
        behaviors[behavior.name] = behavior
    }

    override fun listBehaviors() = behaviors.values
    override fun getBehavior(name: String) = behaviors[name]
}
