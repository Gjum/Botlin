package com.github.gjum.minecraft.botlin.modules.defaults

import com.github.gjum.minecraft.botlin.api.Authentication
import com.github.gjum.minecraft.botlin.api.Avatar
import com.github.gjum.minecraft.botlin.api.Module
import com.github.gjum.minecraft.botlin.api.RateLimitedConnect
import com.github.gjum.minecraft.botlin.modules.ServiceRegistry
import com.github.gjum.minecraft.botlin.util.RateLimiter
import com.github.gjum.minecraft.botlin.util.runWithRateLimitWhereNonNullIsSuccess
import com.github.steveice10.mc.protocol.MinecraftProtocol
import java.util.logging.Logger

class RateLimitedConnectModule : Module() {
    override suspend fun initialize(serviceRegistry: ServiceRegistry, oldModule: Module?) {
        val auth = serviceRegistry.consumeService(Authentication::class.java) ?: return
        val connector = RateLimitedConnectProvider(auth,
            getACProp("Start")?.toIntOrNull() ?: 1000,
            getACProp("Factor")?.toFloatOrNull() ?: 2f,
            getACProp("End")?.toIntOrNull() ?: 30_000,
            getACProp("RateLimit")?.toIntOrNull() ?: 5,
            getACProp("RateInterval")?.toIntOrNull() ?: 60_000
        )
        serviceRegistry.provideService(RateLimitedConnect::class.java, connector)
    }
}

private fun getACProp(prop: String) = System.getProperty("autoconnect$prop") ?: null

internal class RateLimitedConnectProvider(
    private val auth: Authentication,
    override var backoffStart: Int,
    override var backoffFactor: Float,
    override var backoffEnd: Int,
    override var connectRateLimit: Int,
    override var connectRateInterval: Int
) : RateLimitedConnect, RateLimiter.Params {
    private val logger = Logger.getLogger(this.javaClass.name)

    private val rateLimiters = mutableMapOf<String, RateLimiter>()

    override fun limitConnectRate(limit: Int, intervalMs: Int) {
        connectRateLimit = limit
        connectRateInterval = intervalMs
    }

    @Synchronized
    override suspend fun connect(avatar: Avatar) {
        if (avatar.connected) {
            logger.warning("Avatar $avatar is already connected")
        }
        val rateLimiter = rateLimiters.getOrPut(avatar.profile.name, {
            RateLimiter(this)
        })

        var proto: MinecraftProtocol? = null
        // retry until we get a connection or this coroutine is cancelled
        while (proto == null) proto = rateLimiter.runWithRateLimitWhereNonNullIsSuccess {
            auth.authenticate(avatar.profile.name)
        }

        avatar.useProtocol(proto)
    }
}
