package com.github.gjum.minecraft.botlin.data

import com.google.gson.JsonArray

data class ItemInfo(
	val idNr: Int,
	val idName: String,
	val displayName: String,
	val maxStackSize: Int,
	val variantNames: Map<Int, String>,
	var block: BlockInfo? = null
) {
	companion object {
		val AIR = ItemInfo(0, "air", "Air", 0,
			mapOf<Int, String>().withDefault { "Air" },
			null)
	}
}

class ItemInfoStorage(vararg itemsJsons: JsonArray) {
	private val itemsByIdNr = mutableMapOf<Int, ItemInfo>()
	private val itemsByIdName = mutableMapOf<String, ItemInfo>()

	operator fun get(idNr: Int): ItemInfo? {
		return itemsByIdNr[idNr]
	}

	operator fun get(idName: String): ItemInfo? {
		return itemsByIdName[idName]
	}

	fun set(item: ItemInfo) {
		itemsByIdNr[item.idNr] = item
		itemsByIdName[item.idName] = item
	}

	init {
		for (itemJson in itemsJsons.flatMap { it.asIterable() }) {
			val o = itemJson.asJsonObject
			val displayName = o.get("displayName").asString

			val variantNames = o.get("variations")?.asJsonArray?.map {
				Pair(it.asJsonObject["metadata"].asInt,
					it.asJsonObject["displayName"].asString)
			}?.toMap() ?: mapOf()

			val item = ItemInfo(
				idNr = o.get("id").asInt,
				idName = o.get("name").asString,
				displayName = displayName,
				maxStackSize = o.get("stackSize").asInt,
				variantNames = variantNames.withDefault { displayName }
			)
			itemsByIdNr[item.idNr] = item
			itemsByIdName[item.idName] = item
		}
	}
}
