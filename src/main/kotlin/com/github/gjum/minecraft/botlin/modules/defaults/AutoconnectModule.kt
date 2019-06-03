package com.github.gjum.minecraft.botlin.modules.defaults

import com.github.gjum.minecraft.botlin.api.Authentication
import com.github.gjum.minecraft.botlin.api.AutoConnect
import com.github.gjum.minecraft.botlin.api.Module
import com.github.gjum.minecraft.botlin.modules.ServiceRegistry

class AutoConnectModule : Module() {
    override fun initialize(serviceRegistry: ServiceRegistry, oldModule: Module?) {
        val autoConnect = AutoConnectProvider(
            getACProp("Start")?.toIntOrNull() ?: 1000,
            getACProp("Factor")?.toFloatOrNull() ?: 2f,
            getACProp("End")?.toIntOrNull() ?: 30_000,
            getACProp("SuccessCount")?.toIntOrNull() ?: 5,
            getACProp("SuccessInterval")?.toIntOrNull() ?: 30_000
        )
        serviceRegistry.provideService(this, AutoConnect::class.java, autoConnect)

        // if Authentication service is available and mcHost is set,
        // automatically connect to it with the default account
        serviceRegistry.handleServiceChange(this, Authentication::class.java) { auth ->
            val username = auth?.defaultAccount ?: return@handleServiceChange
            val hostProp = System.getProperty("mcHost") ?: return@handleServiceChange
            autoConnect.autoConnectTo(username, hostProp)
        }
    }
}

private fun getACProp(prop: String) = System.getProperty("autoconnect$prop") ?: null

class AutoConnectProvider(
    override var backoffStart: Int,
    override var backoffFactor: Float,
    override var backoffEnd: Int,
    private var successRateCount: Int,
    private var successRateInterval: Int
) : AutoConnect {
    override fun autoConnectTo(username: String, serverAddress: String?) {
        TODO("not implemented")
    }

    override fun getCurrentlyAutoConnecting(): Map<String, String> {
        TODO("not implemented")
    }

    override fun setSuccessRate(successes: Int, intervalMs: Int) {
        TODO("not implemented")
    }
}
