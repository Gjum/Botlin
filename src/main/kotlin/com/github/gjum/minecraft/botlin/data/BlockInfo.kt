package com.github.gjum.minecraft.botlin.data

import com.github.gjum.minecraft.botlin.api.Box
import com.github.gjum.minecraft.botlin.api.Shape
import com.github.gjum.minecraft.botlin.api.Vec3d
import com.github.steveice10.mc.protocol.data.game.world.block.BlockState
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util.logging.Logger

data class BlockStateInfo(
	val stateNr: Int,
	val block: BlockInfo,
	val collisionShape: Shape
) {
	override fun toString() = "BlockStateInfo{${block.idName} ${block.idNr}:$stateNr}"
}

data class BlockInfo(
	val idNr: Int,
	val idName: String,
	val item: ItemInfo,
	val transparent: Boolean,
	val filterLight: Int,
	val emitLight: Int,
	val diggable: Boolean,
	val hardness: Float,
	val droppedItems: Collection<ItemInfo>,
	var states: List<BlockStateInfo>
) {
	val displayName get() = item.displayName

	override fun toString() = "BlockInfo{$idName nr=$idNr}"
}

private fun indexForIdMeta(id: Int, meta: Int) = id * 16 + meta

class BlockInfoStorage(blocksJson: JsonArray, collisionShapesJson: JsonObject, itemInfos: ItemInfoStorage) {
	private val blockStateInfos = mutableMapOf<Int, BlockStateInfo>()
	private val blockInfosByIdName = mutableMapOf<String, BlockInfo>()

	operator fun get(blockState: BlockState): BlockStateInfo? {
		return blockStateInfos[indexForIdMeta(blockState.id, blockState.data)]
	}

	operator fun get(idName: String): BlockInfo? {
		return blockInfosByIdName[idName]
	}

	init {
		val collisionShapes = getShapes(collisionShapesJson)

		for (blockJson in blocksJson) {
			val o = blockJson.asJsonObject
			val idName = o.get("name").asString
			try {
				val block = BlockInfo(
					idNr = o.get("id").asInt,
					idName = idName,
					item = itemInfos[idName]!!,
					transparent = o.get("transparent").asBoolean,
					filterLight = o.getOrNull("filterLight")?.asInt ?: 0,
					emitLight = o.getOrNull("emitLight")?.asInt ?: 0,
					diggable = o.get("diggable").asBoolean,
					hardness = o.getOrNull("hardness")?.asFloat ?: Float.POSITIVE_INFINITY,
					droppedItems = emptyList(), // TODO o.get("drops").asJsonArray.map { itemInfos[it.asJsonObject.get("drop").asInt]!! },
					states = emptyList()
				)

				val collisionShapesByStateIndex = collisionShapes[block.idName]
					?: let {
						Logger.getLogger("BlockInfoStorage").warning(
							"Failed to load collision shape for block '$idName', assuming solid")
						ShapePerBlockState.SOLID
					}
				block.states = (0 until 16).map { meta ->
					BlockStateInfo(
						stateNr = meta,
						block = block,
						collisionShape = collisionShapesByStateIndex[meta]
					).also {
						blockStateInfos[indexForIdMeta(it.block.idNr, it.stateNr)] = it
					}
				}

				blockInfosByIdName[block.idName] = block
				block.item.block = block
			} catch (e: Throwable) {
				System.err.println("while reading block '$idName' $o")
				throw e
			}
		}
	}

	private fun getShapes(shapesJson: JsonObject): Map<String, ShapePerBlockState> {
		val shapes = mutableMapOf<Int, Shape>()
		val blockShapes = mutableMapOf<String, ShapePerBlockState>()
		for ((key, value) in shapesJson.getAsJsonObject("shapes").entrySet()) {
			val boxesJson = value.asJsonArray
			val boxes = mutableListOf<Box>()
			for (element in boxesJson) {
				val a = element.asJsonArray
				var i = 0
				boxes.add(Box(Vec3d(
					a[i++].asDouble,
					a[i++].asDouble,
					a[i++].asDouble), Vec3d(
					a[i++].asDouble,
					a[i++].asDouble,
					a[i++].asDouble)))
			}
			shapes[key.toInt()] = Shape(boxes)
		}
		for ((key, value) in shapesJson.getAsJsonObject("blocks").entrySet()) {
			blockShapes[key] = when {
				value == null || value.isJsonNull -> ShapePerBlockState.EMPTY
				value.isJsonPrimitive -> {
					val shape = shapes[value.asInt]
						?: error("undefined shape id $value in block $key")
					ShapePerBlockState.Same(shape)
				}
				else -> {
					val shapeIds = value.asJsonArray
					val blockStatesShapes = arrayOfNulls<Shape>(shapeIds.size())
					for (i in blockStatesShapes.indices) {
						blockStatesShapes[i] = shapes[shapeIds[i].asInt]
					}
					ShapePerBlockState.OneEach(blockStatesShapes)
				}
			}
		}

		return blockShapes
	}
}

internal sealed class ShapePerBlockState {
	companion object {
		val EMPTY = Same(Shape.EMPTY)
		val SOLID = Same(Shape.SOLID)
	}

	abstract operator fun get(blockStateId: Int): Shape

	internal class Same(private val shape: Shape) : ShapePerBlockState() {
		override operator fun get(blockStateId: Int): Shape {
			return shape
		}
	}

	internal class OneEach(private val shapes: Array<Shape?>) : ShapePerBlockState() {
		override operator fun get(blockStateId: Int): Shape {
			require(blockStateId >= 0) { "block state id < 0" }
			require(blockStateId < shapes.size) {
				("block state id " + blockStateId
					+ " out of bounds for length " + shapes.size)
			}
			return shapes[blockStateId] ?: Shape.EMPTY
		}
	}
}

private fun JsonObject.getOrNull(key: String): JsonElement? {
	return get(key)?.let { if (it.isJsonNull) null else it }
}
