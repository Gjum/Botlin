package com.github.gjum.minecraft.botlin.impl

import com.github.gjum.minecraft.botlin.api.*
import com.github.steveice10.mc.auth.data.GameProfile
import com.github.steveice10.mc.protocol.data.game.chunk.Chunk
import com.github.steveice10.mc.protocol.data.game.chunk.Column
import com.github.steveice10.mc.protocol.data.game.entity.metadata.EntityMetadata
import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position
import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode
import com.github.steveice10.mc.protocol.data.game.entity.type.GlobalEntityType
import com.github.steveice10.mc.protocol.data.game.entity.type.MobType
import com.github.steveice10.mc.protocol.data.game.entity.type.PaintingType
import com.github.steveice10.mc.protocol.data.game.entity.type.`object`.HangingDirection
import com.github.steveice10.mc.protocol.data.game.entity.type.`object`.ObjectData
import com.github.steveice10.mc.protocol.data.game.entity.type.`object`.ObjectType
import com.github.steveice10.mc.protocol.data.game.world.block.BlockState
import com.github.steveice10.mc.protocol.data.message.Message
import java.util.UUID

sealed class MutableEntity(override val eid: Int) : Entity {
	override var uuid: UUID? = null
	override var position: Vec3d? = null
	override var look: Look? = null
	override var velocity: Vec3d? = null
	override var onGround: Boolean? = null
	override var headYaw: Double? = null
	override var passengers: List<MutableEntity> = emptyList()
	override var vehicle: MutableEntity? = null
	override var health: Float? = null
	override val metadata = mutableMapOf<Int, EntityMetadata>()

	/**
	 * Call this before respawning to mark fields as unknown.
	 */
	fun reset() {
		position = null
		look = null
		velocity = null
		onGround = null
		headYaw = null
		passengers = emptyList()
		vehicle = null
		health = null
		metadata.clear()
	}

	fun updateMetadata(metadataNew: Array<EntityMetadata>) {
		for (meta in metadataNew) {
			this.metadata[meta.id] = meta
		}
	}
}

class MutableExpEntity(eid: Int, override var exp: Int) : MutableEntity(eid), ExpEntity

class MutableMobEntity(eid: Int, override var type: MobType) : MutableEntity(eid), MobEntity

class MutableObjectEntity(eid: Int, override var type: ObjectType, override var data: ObjectData?) : MutableEntity(eid), ObjectEntity

class MutablePaintingEntity(eid: Int, override var paintingType: PaintingType, override var direction: HangingDirection) : MutableEntity(eid), PaintingEntity

class MutableGlobalEntity(eid: Int, override var type: GlobalEntityType) : MutableEntity(eid), GlobalEntity

class MutablePlayerEntity(eid: Int) : MutableEntity(eid), PlayerEntity {
	constructor(entity: MutableEntity) : this(entity.eid) {
		uuid = entity.uuid
		position = entity.position
		look = entity.look
		velocity = entity.velocity
		onGround = entity.onGround
		headYaw = entity.headYaw
		passengers = entity.passengers
		vehicle = entity.vehicle
		health = entity.health
		metadata.clear()
		metadata.putAll(entity.metadata)
	}

	override var gameMode: GameMode? = null
	override var food: Int? = null
	override var saturation: Float? = null
	override var experience: Experience? = null
	override var playerListItem: MutablePlayerListItem? = null
}

class MutablePlayerListItem(override val profile: GameProfile) : PlayerListItem {
	override var displayName: Message? = null
	override var gameMode: GameMode? = null
	override var ping: Int? = null
	override var entity: PlayerEntity? = null
}

class MutableWorld(override val dimension: Int) : World {
	override val chunkColumns: MutableMap<ChunkPos, Column> = mutableMapOf()
	override val entities = mutableMapOf<Int, MutableEntity>()

	override var rainy: Boolean? = null
	override var skyDarkness: Double? = null

	private fun indexBlockInSection(x: Int, y: Int, z: Int) = (y.mod16 shl 8) or (z.mod16 shl 4) or x.mod16

	fun getBlockState(x: Int, y: Int, z: Int): BlockState? {
		return getSectionForBlock(x, y, z)?.blocks?.get(x.mod16, y.mod16, z.mod16)
	}

	private fun getColumnForBlock(x: Int, z: Int): Column? {
		return chunkColumns[ChunkPos.fromBlock(x, z)]
	}

	private fun getSectionForBlock(x: Int, y: Int, z: Int): Chunk? {
		return getColumnForBlock(x, z)?.chunks?.get(y.div16)
	}

	fun setBlockState(position: Position, block: BlockState) {
		position.apply {
			val section = getSectionForBlock(x, y, z) ?: return
			section.blocks.set(x.mod16, y.mod16, z.mod16, com.github.steveice10.mc.protocol.data.game.world.block.BlockState(block.id))
		}
	}

	fun unloadColumn(x: Int, z: Int) {
		chunkColumns.remove(ChunkPos(x, z))
	}

	fun updateColumn(x: Int, z: Int, column: Column) {
		// TODO apply partial updates - i.e., when sections are missing
		chunkColumns[ChunkPos(x, z)] = column
	}
}
