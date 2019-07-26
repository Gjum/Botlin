package com.github.gjum.minecraft.botlin.behaviors

import com.github.gjum.minecraft.botlin.api.*
import com.github.gjum.minecraft.botlin.util.RateLimiter
import com.github.gjum.minecraft.botlin.scripting.ScriptModule
import com.github.gjum.minecraft.botlin.scripting.awaitEventCondition
import java.util.logging.Logger

open class AutoReconnect : ScriptModule() {
	override val name = "autoreconnect"

	override suspend fun script(serviceRegistry: ServiceRegistry) {
		val avatar = serviceRegistry.consumeService(Avatar::class.java)!!
		val auth = serviceRegistry.consumeService(Authentication::class.java)!!
		val rateLimiter = RateLimiter(RateParamsFromSysProps())
		while (true) {
			avatar.awaitEventCondition(AvatarEvent.Disconnected::class.java) { !avatar.connected }
			// TODO lock avatar
			rateLimiter.runWithRateLimit {
				val (proto, authError) = auth.authenticate()
				if (proto == null) {
					Logger.getLogger(javaClass.name).warning("Failed to authenticate")
					null to false // TODO this api sucks
				} else {
					avatar.connect(proto)
					null to true // TODO this api sucks
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
