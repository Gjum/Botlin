package com.github.gjum.minecraft.botlin.data

import com.github.steveice10.mc.protocol.data.game.world.block.BlockState
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.stream.JsonReader
import java.io.FileReader

class MinecraftData(mcDataDir: String) {
	val items = ItemInfoStorage(
		// in 1.12, blocks are items too
		loadJson("$mcDataDir/blocks.json", JsonArray::class.java),
		loadJson("$mcDataDir/items.json", JsonArray::class.java))

	val blocks = BlockInfoStorage(
		loadJson("$mcDataDir/blocks.json", JsonArray::class.java),
		loadJson("$mcDataDir/blockCollisionShapes.json", JsonObject::class.java),
		items)

	fun getBlockStateInfo(blockState: BlockState) = blocks[blockState]
	fun getBlockInfo(blockId: String) = blocks[blockId]
	fun getItemInfo(itemId: String) = items[itemId]
	fun getItemInfo(itemNr: Int) = items[itemNr]
}

private fun <T> loadJson(path: String, type: Class<T>): T {
	return Gson().fromJson(JsonReader(FileReader(path)), type)
}
