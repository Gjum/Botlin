package com.github.gjum.minecraft.botlin.modules.defaults

import com.github.gjum.minecraft.botlin.api.Authentication
import com.github.gjum.minecraft.botlin.api.Avatar
import com.github.gjum.minecraft.botlin.api.Module
import com.github.gjum.minecraft.botlin.api.RateLimitedConnect
import com.github.gjum.minecraft.botlin.modules.ServiceRegistry
import com.github.steveice10.mc.protocol.MinecraftProtocol
import com.github.steveice10.packetlib.Client
import com.github.steveice10.packetlib.tcp.TcpSessionFactory
import java.lang.Long.max
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
    var connectRateLimit: Int,
    var connectRateInterval: Int
) : RateLimitedConnect {
    private val logger = Logger.getLogger(this.javaClass.name)

    private val rateLimiters = mutableMapOf<String, AccountRateLimiter>()

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
            AccountRateLimiter(avatar.profile.name, this)
        })

        var proto: MinecraftProtocol? = null
        // retry until we get a connection or this coroutine is cancelled
        while (proto == null) proto = rateLimiter.createProto(auth)

        val split = avatar.serverAddress.split(':')
        val host = split[0]
        val port = split[1].toInt()
        val client = Client(host, port, proto, TcpSessionFactory())
        if (avatar.connected) {
            logger.warning("Avatar $avatar got connected by other caller")
            return
        }
        avatar.useConnection(client.session)
        client.session.connect(true)
    }
}

/** For testing. Gets inlined by JVM JIT (hopefully). */
internal object TimeProxy {
    fun currentTimeMillis() = System.currentTimeMillis()
    suspend fun delay(ms: Long) = kotlinx.coroutines.delay(ms)
}

internal class AccountRateLimiter(private val username: String, private val parent: RateLimitedConnectProvider) {
    private val rateQueue = mutableListOf<Long>()
    private var nextBackoff: Float = parent.backoffStart.toFloat() / parent.backoffFactor
    private var prevAttempt = 0L // at most now - backoffStart / backoffFactor

    @Synchronized
    suspend fun createProto(auth: Authentication): MinecraftProtocol? {
        val callTime = TimeProxy.currentTimeMillis()

        // remove unused entries
        while (rateQueue.isNotEmpty() && rateQueue[0] < callTime - parent.connectRateInterval) rateQueue.removeAt(0)
        // apply success rate limit
        val nextRateSlot = if (rateQueue.size >= parent.connectRateLimit) {
            rateQueue[0] + parent.connectRateInterval
        } else callTime

        val nextAttempt = prevAttempt + nextBackoff.toLong()
        val waitMs = max(nextAttempt - callTime, nextRateSlot - callTime)
        if (waitMs > 0) TimeProxy.delay(waitMs)

        prevAttempt = TimeProxy.currentTimeMillis()
        val protocol = auth.authenticate(username)

        val successfulConnect = protocol != null
        if (successfulConnect) {
            rateQueue.add(prevAttempt)
            nextBackoff = parent.backoffStart.toFloat()
        } else {
            nextBackoff *= parent.backoffFactor
        }
        if (nextBackoff > parent.backoffEnd) nextBackoff = parent.backoffEnd.toFloat()

        return protocol
    }
}
