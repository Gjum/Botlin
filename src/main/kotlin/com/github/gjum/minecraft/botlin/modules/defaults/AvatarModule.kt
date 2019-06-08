package com.github.gjum.minecraft.botlin.modules.defaults

import com.github.gjum.minecraft.botlin.McBot
import com.github.gjum.minecraft.botlin.api.Avatar
import com.github.gjum.minecraft.botlin.api.AvatarService
import com.github.gjum.minecraft.botlin.api.Module
import com.github.gjum.minecraft.botlin.modules.ServiceRegistry

class AvatarProvider(private val avatars: MutableMap<String, Avatar>) : AvatarService {
    override fun getAvatar(username: String): Avatar {
        return avatars.getOrPut(username) { McBot() }
    }
}

// TODO hot reload should retain previous avatars' state
class AvatarModule : Module() {
    override fun initialize(serviceRegistry: ServiceRegistry, oldModule: Module?) {
        serviceRegistry.provideService(AvatarService::class.java,
            AvatarProvider(mutableMapOf()))
    }
}
