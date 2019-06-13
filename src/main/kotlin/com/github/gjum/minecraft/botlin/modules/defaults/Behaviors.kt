package com.github.gjum.minecraft.botlin.modules.defaults

import com.github.gjum.minecraft.botlin.api.*
import com.github.gjum.minecraft.botlin.modules.ServiceRegistry

class BehaviorRegistryModule : Module() {
    override fun initialize(serviceRegistry: ServiceRegistry, oldModule: Module?) {
        val registry = BehaviorRegistryProvider()
        serviceRegistry.provideService(BehaviorRegistry::class.java, registry)
    }
}

class BehaviorRegistryProvider : BehaviorRegistry {
    private val behaviors = mutableMapOf<String, Behavior>()

    override fun provideBehavior(behavior: Behavior) {
        behaviors[behavior.name] = behavior
    }

    override fun listBehaviors() = behaviors.values
    override fun getBehavior(name: String) = behaviors[name]
}
