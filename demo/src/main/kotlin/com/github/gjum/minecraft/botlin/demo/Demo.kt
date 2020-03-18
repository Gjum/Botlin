package com.github.gjum.minecraft.botlin.demo

import com.github.gjum.minecraft.botlin.api.Slot
import com.github.gjum.minecraft.botlin.api.Vec3d
import com.github.gjum.minecraft.botlin.impl.setupBot
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
	val arrival = async { bot.moveStraightBy(Vec3d(1.0, 0.0, 0.0)) }
	bot.jumpUntilLanded()
	withTimeout(1000) { arrival.await() }

	// search inventory for any block with a bounding box
	fun Slot.canPillar(): Boolean {
		val block = bot.mcData.getItemInfo(itemId)?.block ?: return false
		return block.defaultState.collisionShape.boxes.isNotEmpty()
	}

	val blockSlot = bot.window!!.slots.indexOfFirst { it.canPillar() }
	if (blockSlot != -1) bot.swapHotbar(blockSlot, bot.hotbarSelection!!)

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
