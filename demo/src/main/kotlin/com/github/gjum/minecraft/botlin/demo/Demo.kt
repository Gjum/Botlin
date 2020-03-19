package com.github.gjum.minecraft.botlin.demo

import com.github.gjum.minecraft.botlin.api.forward
import com.github.gjum.minecraft.botlin.api.isSolid
import com.github.gjum.minecraft.botlin.api.setupBot
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerSwingArmPacket
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

fun main() = runBlocking {
	val bot = setupBot("Botlin")
	bot.connect("localhost:25565") // waits until spawned

	if (!bot.alive) bot.respawn() // check bot state

	bot.chat("hello") // rate limited chat
	bot.sendPacket(ClientPlayerSwingArmPacket(Hand.MAIN_HAND)) // send raw packets for not-yet implemented features

	// jump while moving
	val arrival = async { bot.moveStraightBy(bot.forward.asVec3d) }
	bot.jumpUntilLanded()
	withTimeout(1000) { arrival.await() }

	// search inventory for any solid block (with a bounding box)
	bot.holdItem {
		if (it.empty) return@holdItem false
		val block = bot.mcData.getItemInfo(it.itemId)?.block
			?: return@holdItem false // item is not a block
		return@holdItem block.defaultState.isSolid
	}

	// pillar up
	val floorPos = bot.playerEntity!!.position!!
	bot.jumpToHeight(1.0)
	bot.placeBlock(floorPos)

	// TODO eating, crafting - using helper method
	// TODO digging
	// TODO duringReinforcementFortifying
	// TODO stepping, swimming
	// TODO vehicle movement

	bot.disconnect()
}
