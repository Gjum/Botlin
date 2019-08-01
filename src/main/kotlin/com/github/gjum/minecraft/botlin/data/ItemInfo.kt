package com.github.gjum.minecraft.botlin.data

import com.google.gson.JsonArray

data class ItemInfo(
	val nr: Int,
	val id: String,
	val displayName: String,
	val stackSize: Int,
	var block: BlockInfo? = null
)

class ItemInfoStorage(itemsJson: JsonArray) {
	private val itemsByNr = mutableMapOf<Int, ItemInfo>()
	private val itemsById = mutableMapOf<String, ItemInfo>()

	operator fun get(itemId: String): ItemInfo? {
		return itemsById[itemId]
	}

	operator fun get(itemNr: Int): ItemInfo? {
		return itemsByNr[itemNr]
	}

	init {
		for (itemJson in itemsJson) {
			val o = itemJson.asJsonObject
			val item = ItemInfo(
				nr = o.get("id").asInt,
				id = o.get("name").asString,
				displayName = o.get("displayName").asString,
				stackSize = o.get("stackSize").asInt
			)
			itemsByNr[item.nr] = item
			itemsById[item.id] = item
		}
	}
}
