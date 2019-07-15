package com.github.gjum.minecraft.botlin.util

import com.github.gjum.minecraft.botlin.api.Module
import com.github.gjum.minecraft.botlin.api.ServiceRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.EmptyCoroutineContext

abstract class ScriptModule : Module() {
	private val scope = CoroutineScope(EmptyCoroutineContext)
	private var scriptJob: Job? = null

	abstract suspend fun script(coroutineScope: CoroutineScope, serviceRegistry: ServiceRegistry)

	@Synchronized
	override suspend fun activate(serviceRegistry: ServiceRegistry) {
		super.activate(serviceRegistry)
		// XXX in which scope should scripts be launched?
		scriptJob = scope.launch { script(this, serviceRegistry) }
	}

	@Synchronized
	override fun deactivate() {
		super.deactivate()
		scriptJob?.cancel()
		// TODO this might be enough?
		scope.coroutineContext[Job]?.cancel()
	}
}
