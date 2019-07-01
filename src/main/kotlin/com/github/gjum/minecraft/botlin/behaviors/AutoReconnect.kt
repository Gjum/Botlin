package com.github.gjum.minecraft.botlin.behaviors

import com.github.gjum.minecraft.botlin.api.Authentication
import com.github.gjum.minecraft.botlin.api.AvatarEvents
import com.github.gjum.minecraft.botlin.api.ServiceRegistry
import com.github.gjum.minecraft.botlin.modules.consumeService
import com.github.gjum.minecraft.botlin.util.ModuleAutoEvents
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.logging.Logger

// XXX do rate limiting, use event scopes with coroutine job script

open class AutoReconnect : ModuleAutoEvents() {
	override val name = "autoreconnect"

	private var auth: Authentication? = null
	private var connectJob: Job? = null

	override suspend fun activate(serviceRegistry: ServiceRegistry) {
		super.activate(serviceRegistry)
		auth = serviceRegistry.consumeService(Authentication::class.java)!!
		onEach(AvatarEvents.Disconnected::class.java) { connect() }
		connect()
	}

	@Synchronized
	private fun connect() {
		if (avatar.connected) return
		if (connectJob != null) return
		connectJob = GlobalScope.launch {
			val proto = auth!!.authenticate()
			if (proto == null) {
				Logger.getLogger(javaClass.name).warning("Failed to authenticate")
				// XXX retry later
			} else {
				avatar.useProtocol(proto)
			}
		}
		connectJob!!.invokeOnCompletion { connectJob = null }
	}
}
