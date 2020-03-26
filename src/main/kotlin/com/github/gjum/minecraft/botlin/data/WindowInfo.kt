package com.github.gjum.minecraft.botlin.data

import com.github.steveice10.mc.protocol.data.MagicValues
import com.github.steveice10.mc.protocol.data.game.window.WindowType
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

sealed class OpenMethod {
	data class OpenWithBlock(val blockId: Int) : OpenMethod()
	data class OpenWithEntity(val entityId: Int) : OpenMethod()
}

data class WindowInfo(
	val id: String,
	val name: String,
	val slots: Map<String, IntRange>,
	val properties: List<String>,
	val openedWith: List<OpenMethod>
) {

	override fun toString() = "WindowInfo{$name}"
}

class WindowInfoStorage(windowsJson: JsonArray) {
	private val windowInfosById = mutableMapOf<WindowType?, WindowInfo>()

	operator fun get(windowType: WindowType?): WindowInfo? {
		return windowInfosById[windowType]
	}

	init {
		for (windowJson in windowsJson) {
			val o = windowJson.asJsonObject
			val id = o.get("id").asString
			try {
				val window = WindowInfo(
					id = id,
					name = o.get("name").asString,
					slots = o.getOrNull("slots")?.asJsonArray?.map {
						val s = it.asJsonObject
						val start = s.get("index").asInt
						val size = s.getOrNull("size")?.asInt ?: 1
						Pair(s.get("name").asString,
							start until start + size)
					}?.toMap() ?: emptyMap(),
					properties = o.getOrNull("properties")?.asJsonArray?.map { it.asString }?.toList() ?: emptyList(),
					openedWith = o.getOrNull("openedWith")?.asJsonArray?.map {
						when (it.asJsonObject.get("type").asString) {
							"block" -> OpenMethod.OpenWithBlock(it.asJsonObject.get("id").asInt)
							"entity" -> OpenMethod.OpenWithEntity(it.asJsonObject.get("id").asInt)
							else -> error("Unknown open method '${it.asJsonObject.get("type")}'")
						}
					}?.toList() ?: emptyList()
				)
				val windowType = when (window.id) {
					"" -> null
					else -> MagicValues.key(WindowType::class.java, window.id)
				}
				windowInfosById[windowType] = window
			} catch (e: Throwable) {
				System.err.println("while reading window '$id' $o")
				throw e
			}
		}
	}
}

private fun JsonObject.getOrNull(key: String): JsonElement? {
	return get(key)?.let { if (it.isJsonNull) null else it }
}
