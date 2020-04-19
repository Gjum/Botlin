package com.github.gjum.minecraft.botlin.demo

import com.github.gjum.minecraft.botlin.api.forward
import com.github.gjum.minecraft.botlin.api.getOrThrow
import com.github.gjum.minecraft.botlin.api.isSolid
import com.github.gjum.minecraft.botlin.api.runBotScript
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerSwingArmPacket
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

fun main() = runBotScript("Botlin") {
	connect("localhost:25565") // waits until spawned

	if (!alive) respawn() // check bot state

	chat("hello") // rate limited chat
	sendPacket(ClientPlayerSwingArmPacket(Hand.MAIN_HAND)) // send raw packets for not-yet implemented features

	delay(1000)
	println("searching inventory for any solid block (with a bounding box)")

	val slot = holdItem { slot ->
		if (slot.empty) return@holdItem false
		val block = slot.item.block
			?: return@holdItem false // item is not a block
		return@holdItem block.states[0].isSolid
	}.getOrThrow()

	delay(1000)
	println("pillaring up with ${slot.displayName}")

	val floorPos = playerEntity!!.position!!
	jumpByHeight(1.0)
	placeBlock(floorPos)

	delay(1000)
	println("jumping while moving")
	coroutineScope {
		// all actions inside this scope must complete before this scope completes

		val arrival = async {
			moveStraightBy(forward)
		}
		// while moving, jump
		jumpUntilLanded()

		withTimeout(1000) {
			arrival.await()
		}.getOrThrow()
	}

	// TODO eating, crafting - using helper method
	// TODO digging
	// TODO duringReinforcementFortifying
	// TODO swimming
	// TODO vehicle movement
}
