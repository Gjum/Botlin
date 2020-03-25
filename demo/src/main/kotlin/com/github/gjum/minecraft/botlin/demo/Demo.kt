package com.github.gjum.minecraft.botlin.demo

import com.github.gjum.minecraft.botlin.api.forward
import com.github.gjum.minecraft.botlin.api.getOrThrow
import com.github.gjum.minecraft.botlin.api.isSolid
import com.github.gjum.minecraft.botlin.api.setupBot
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerSwingArmPacket
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

fun main() = runBlocking {
	setupBot("Botlin").run {
		try {
			connect("localhost:25565") // waits until spawned

			if (!alive) respawn() // check bot state

			chat("hello") // rate limited chat
			sendPacket(ClientPlayerSwingArmPacket(Hand.MAIN_HAND)) // send raw packets for not-yet implemented features
			delay(1000)
			println("jumping while moving")

			val arrival = async { moveStraightBy(forward) }
			jumpUntilLanded()
			withTimeout(1000) { arrival.await() }.getOrThrow()

			delay(1000)
			println("searching inventory for any solid block (with a bounding box)")

			val slot = holdItem { slot ->
				if (slot.empty) return@holdItem false
				val block = mcData.getItemInfo(slot.itemId)?.block
					?: return@holdItem false // item is not a block
				return@holdItem block.states[0].isSolid
			}.getOrThrow()

			delay(1000)
			println("pillaring up with ${slot.name}")

			val floorPos = playerEntity!!.position!!
			jumpByHeight(1.0)
			placeBlock(floorPos)

			// TODO eating, crafting - using helper method
			// TODO digging
			// TODO duringReinforcementFortifying
			// TODO swimming
			// TODO vehicle movement
		} finally {
			disconnect()
		}
	}
}
