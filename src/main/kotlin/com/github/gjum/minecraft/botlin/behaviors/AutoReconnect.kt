package com.github.gjum.minecraft.botlin.behaviors

import com.github.gjum.minecraft.botlin.api.Authentication
import com.github.gjum.minecraft.botlin.api.Avatar
import com.github.gjum.minecraft.botlin.api.AvatarEvents
import com.github.gjum.minecraft.botlin.api.ServiceRegistry
import com.github.gjum.minecraft.botlin.modules.consumeService
import com.github.gjum.minecraft.botlin.util.ModuleAutoEvents
import kotlinx.coroutines.runBlocking
import java.util.logging.Logger

open class AutoReconnect : ModuleAutoEvents() {
	override val name = "autoreconnect"

	override suspend fun activate(serviceRegistry: ServiceRegistry, avatar: Avatar) {
		super.activate(serviceRegistry, avatar)
		val auth = serviceRegistry.consumeService(Authentication::class.java)!!
		onEach(AvatarEvents.Disconnected::class.java) {
			val proto = runBlocking { auth.authenticate() }
			if (proto == null) {
				Logger.getLogger(javaClass.name).warning("Failed to authenticate")
			} else {
				avatar.useProtocol(proto)
			}
		}
	}
}
