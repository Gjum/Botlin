package com.github.gjum.minecraft.botlin.behaviors

import com.github.gjum.minecraft.botlin.api.*
import com.github.gjum.minecraft.botlin.modules.consumeService
import com.github.gjum.minecraft.botlin.util.RateLimiter
import com.github.gjum.minecraft.botlin.util.ScriptModule
import com.github.gjum.minecraft.botlin.util.awaitEventCondition
import kotlinx.coroutines.*
import java.util.logging.Logger

open class AutoReconnect : ScriptModule() {
	override val name = "autoreconnect"

	override suspend fun script(coroutineScope: CoroutineScope, serviceRegistry: ServiceRegistry) {
		val avatar = serviceRegistry.consumeService(Avatar::class.java)!!
		val auth = serviceRegistry.consumeService(Authentication::class.java)!!
		val rateLimiter = RateLimiter(RateParamsFromSysProps())
		while (coroutineScope.isActive) {
			avatar.awaitEventCondition(AvatarEvents.Disconnected::class.java) { !avatar.connected }
			rateLimiter.runWithRateLimit {
				val proto = auth.authenticate()
				if (proto == null) {
					Logger.getLogger(javaClass.name).warning("Failed to authenticate")
					null to false
				} else {
					avatar.useProtocol(proto)
					null to true
				}
			}
		}
	}
}

private class RateParamsFromSysProps : RateLimiter.Params {
	override var backoffStart = System.getProperty(
		"authBackoffStart")?.toIntOrNull()
		?: 1000
	override var backoffFactor = System.getProperty(
		"authBackoffFactor")?.toFloatOrNull()
		?: 2f
	override var backoffEnd = System.getProperty(
		"authBackoffEnd")?.toIntOrNull()
		?: 30_000
	override var connectRateLimit = System.getProperty(
		"authRateLimit")?.toIntOrNull()
		?: 5
	override var connectRateInterval = System.getProperty(
		"authRateInterval")?.toIntOrNull()
		?: 60_000
}
