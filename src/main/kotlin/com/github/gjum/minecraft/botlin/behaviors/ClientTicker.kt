package com.github.gjum.minecraft.botlin.behaviors

import com.github.gjum.minecraft.botlin.api.*
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerMovementPacket
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerPositionPacket
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerPositionRotationPacket
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerRotationPacket
import com.github.steveice10.mc.protocol.packet.ingame.client.world.ClientTeleportConfirmPacket
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.player.ServerPlayerPositionRotationPacket
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
		if (event.packet !is ServerPlayerPositionRotationPacket) return

		bot.post(ClientTeleportConfirmPacket(event.packet.teleportId))
		bot.playerEntity!!.apply {
			bot.sendPacket(
				ClientPlayerPositionRotationPacket(
					(onGround ?: true),
					position!!.x,
					position!!.y,
					position!!.z,
					look!!.yawDegrees.toFloat(),
					look!!.pitchDegrees.toFloat()
				)
			)

		}
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
			val prevPos = position
			val prevLook = look

			bot.post(AvatarEvent.PreClientTick())

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
		}

		bot.post(AvatarEvent.ClientTick())
	}
}
