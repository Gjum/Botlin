package com.github.gjum.minecraft.botlin.util

import com.github.gjum.minecraft.botlin.api.Avatar
import com.github.gjum.minecraft.botlin.api.Module
import com.github.gjum.minecraft.botlin.api.ServiceRegistry
import com.github.gjum.minecraft.botlin.modules.consumeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

abstract class ScriptModule : Module() {
	private var scriptJob: Job? = null

	abstract suspend fun script(coroutineScope: CoroutineScope, serviceRegistry: ServiceRegistry)

	@Synchronized
	override suspend fun activate(serviceRegistry: ServiceRegistry) {
		super.activate(serviceRegistry)
		val avatar = serviceRegistry.consumeService(Avatar::class.java)!!
		scriptJob = avatar.launch { script(this, serviceRegistry) }
	}

	@Synchronized
	override fun deactivate() {
		super.deactivate()
		scriptJob?.cancel()
	}
}
