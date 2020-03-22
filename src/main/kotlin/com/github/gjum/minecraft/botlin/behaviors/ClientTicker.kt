package com.github.gjum.minecraft.botlin.behaviors

import com.github.gjum.minecraft.botlin.api.*
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerMovementPacket
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerPositionPacket
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerPositionRotationPacket
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerRotationPacket
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.logging.Logger
import kotlin.concurrent.fixedRateTimer

class ClientTicker(private val bot: ApiBot) : ChildScope(bot) {
	private var ticker: Job? = null

	private var prevPos: Vec3d? = null
	private var prevLook: Look? = null

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
		if (!bot.ingame) return

		bot.playerEntity?.apply {
			launch {
				bot.post(AvatarEvent.PreClientTick())
					.await()

				if (position != null && look != null) {
					if (position != prevPos) {
						if (look != prevLook) {
							bot.sendPacket(ClientPlayerPositionRotationPacket(
								onGround ?: true,
								position!!.x,
								position!!.y,
								position!!.z,
								look!!.yawDegrees.toFloat(),
								look!!.pitchDegrees.toFloat()))
						} else {
							bot.sendPacket(ClientPlayerPositionPacket(
								onGround ?: true,
								position!!.x,
								position!!.y,
								position!!.z))
						}
					} else {
						if (look != prevLook) {
							bot.sendPacket(ClientPlayerRotationPacket(
								onGround ?: true,
								look!!.yawDegrees.toFloat(),
								look!!.pitchDegrees.toFloat()))
						} else {
							bot.sendPacket(ClientPlayerMovementPacket((onGround
								?: true)))
						}
					}
				}

				prevPos = position
				prevLook = look
			}

			bot.post(AvatarEvent.ClientTick())
		}
	}
}
