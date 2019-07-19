package com.github.gjum.minecraft.botlin.util

import com.github.gjum.minecraft.botlin.api.Module
import com.github.gjum.minecraft.botlin.api.ServiceRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.logging.Level
import java.util.logging.Logger

abstract class ScriptModule : Module() {
	private var scriptJob: Job? = null

	abstract suspend fun script(serviceRegistry: ServiceRegistry)

	override suspend fun activate(serviceRegistry: ServiceRegistry) {
		super.activate(serviceRegistry)
		// Scripts get cancelled when module system gets torn down.
		// Scripts block parent coroutine from stopping (currently the runBlocking{init()} in Main).
		scriptJob = serviceRegistry.launch { script(serviceRegistry) }
		scriptJob!!.invokeOnCompletion { e ->
			if (e != null && e !is CancellationException) {
				Logger.getLogger(javaClass.name).log(Level.WARNING,
					"Script '$name' failed: $e", e)
				// TODO emit event?
			}
		}
	}

	override fun deactivate() {
		super.deactivate()
		scriptJob?.cancel()
	}
}
