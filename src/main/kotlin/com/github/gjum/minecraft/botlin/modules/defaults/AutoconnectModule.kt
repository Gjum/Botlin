package com.github.gjum.minecraft.botlin.modules.defaults

import com.github.gjum.minecraft.botlin.api.*
import com.github.gjum.minecraft.botlin.modules.ServiceRegistry
import kotlinx.coroutines.*
import java.lang.Long.max

class AutoConnectModule : Module() {
    override fun initialize(serviceRegistry: ServiceRegistry, oldModule: Module?) {
        val autoConnect = AutoConnectProvider(
            getACProp("Start")?.toIntOrNull() ?: 1000,
            getACProp("Factor")?.toFloatOrNull() ?: 2f,
            getACProp("End")?.toIntOrNull() ?: 30_000,
            getACProp("RateLimit")?.toIntOrNull() ?: 5,
            getACProp("RateInterval")?.toIntOrNull() ?: 60_000
        )
        serviceRegistry.provideService(AutoConnect::class.java, autoConnect)

        // if Authentication service is available and mcHost is set,
        // automatically connect to it with the default account
        serviceRegistry.consumeService(Authentication::class.java) { auth ->
            val username = auth?.defaultAccount ?: return@consumeService
            val hostProp = System.getProperty("mcHost") ?: return@consumeService
            autoConnect.autoConnectTo(username, hostProp)
        }

        serviceRegistry.consumeService(CommandService::class.java) { commands ->
            commands ?: return@consumeService
            commands.registerCommand("connect",
                "connect [account=default] [server=localhost:25565]",
                "Start auto-connecting account to server."
            ) { cmdLine, context ->
                TODO("connect command")
            }
            commands.registerCommand("logout",
                "logout [account]",
                "Disconnect account from server."
            ) { cmdLine, context ->
                TODO("logout command")
            }
        }
    }
}

private fun getACProp(prop: String) = System.getProperty("autoconnect$prop") ?: null

class AutoConnectProvider(
    override var backoffStart: Int,
    override var backoffFactor: Float,
    override var backoffEnd: Int,
    var connectRateLimit: Int,
    var connectRateInterval: Int
) : AutoConnect {
    private val autoConnectStates = mutableMapOf<String, AutoConnectAccount>()

    override fun autoConnectTo(username: String, serverAddress: String?) {
        runBlocking {
            autoConnectStates.remove(username)
                ?.stop()
        }
        serverAddress ?: return
        autoConnectStates[username] = AutoConnectAccount(username, serverAddress, this)
    }

    override fun getCurrentlyAutoConnecting(): Map<String, String> {
        return autoConnectStates.mapValues { e -> e.value.serverAddress }
    }

    override fun limitConnectRate(limit: Int, intervalMs: Int) {
        connectRateLimit = limit
        connectRateInterval = intervalMs
    }

    /**
     * Tries to connect the username to the server.
     * Returns true if the connection was successful.
     */
    fun connect(username: String, serverAddress: String): Boolean {
        val split = serverAddress.split(':')
        val host = split.getOrNull(0) ?: "localhost"
        val port = split.getOrNull(1)?.toInt() ?: 25565
        TODO("get avatar, run connection")
    }
}

class AutoConnectAccount(
    val username: String,
    val serverAddress: String,
    private val parent: AutoConnectProvider
) {
    private var timer: Job = GlobalScope.launch {
        var nextBackoff: Float = parent.backoffStart.toFloat()
        val rateQueue = mutableListOf<Long>()
        while (isActive) {
            val currentAttempt = System.currentTimeMillis()
            rateQueue.add(currentAttempt)

            val success = try {
                parent.connect(username, serverAddress)
            } catch (e: Exception) {
                false
            }

            // next attempt is based on backoff calculated after previous attempt, not current
            val nextAttempt = (currentAttempt + nextBackoff).toLong()

            // update backoff for second-next attempt
            if (success) {
                nextBackoff = parent.backoffStart.toFloat()
            } else {
                nextBackoff *= parent.backoffFactor
            }
            if (nextBackoff > parent.backoffEnd) nextBackoff = parent.backoffEnd.toFloat()

            // remove unused entries
            val now = System.currentTimeMillis()
            while (rateQueue[0] < now - parent.connectRateInterval) rateQueue.removeAt(0)

            // apply rate limit
            val nextRateSlot = if (rateQueue.size >= parent.connectRateLimit) {
                rateQueue[0] + parent.connectRateInterval
            } else now

            val waitMs = max(nextAttempt - currentAttempt, nextRateSlot - now)
            delay(waitMs) // wait for next attempt
        }
    }

    @Synchronized
    suspend fun stop() {
        timer.cancelAndJoin()
    }
}
