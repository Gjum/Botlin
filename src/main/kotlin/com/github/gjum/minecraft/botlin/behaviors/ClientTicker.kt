package com.github.gjum.minecraft.botlin.behaviors

import com.github.gjum.minecraft.botlin.api.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.logging.Logger
import kotlin.concurrent.fixedRateTimer

class ClientTicker(private val bot: ApiBot) : ChildScope(bot) {
	private var ticker: Job? = null

	init {
		launch { bot.onEach(::onSpawned) }
		launch { bot.onEach(::onDisconnected) }
	}

	private fun onSpawned(event: AvatarEvent.TeleportedByServer) {
		startTicker()
	}

	private fun onDisconnected(event: AvatarEvent.Disconnected) {
		ticker?.cancel()
		ticker = null
	}

	private fun startTicker() {
		if (ticker != null) return
		ticker = launch {
			val timer = fixedRateTimer(period = 50) { doTick() }
			// bind timer lifetime to coroutineContext
			try {
				suspendCancellableCoroutine { } // wait until ticker is cancelled
			} finally {
				Logger.getLogger("ClientTicker").fine("Cancelling tick timer")
				timer.cancel()
			}
		}
	}

	private fun doTick() {
		if (bot.ingame) bot.post(AvatarEvent.ClientTick())
	}
}
