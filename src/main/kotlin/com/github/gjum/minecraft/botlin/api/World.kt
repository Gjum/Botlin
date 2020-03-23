package com.github.gjum.minecraft.botlin.api

import com.github.steveice10.mc.auth.data.GameProfile
import com.github.steveice10.mc.protocol.data.game.chunk.Column
import com.github.steveice10.mc.protocol.data.game.entity.metadata.EntityMetadata
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
import kotlin.experimental.and

/**
 * Properties that have not yet been received from the server are null.
 */
interface Entity {
	val eid: Int
	val uuid: UUID?
	val position: Vec3d?
	val look: Look?
	val onGround: Boolean?
	val velocity: Vec3d?
	val headYaw: Double?
	val passengers: List<Entity>
	val vehicle: Entity?
	val health: Float?
	val metadata: Map<Int, EntityMetadata>

	// TODO access arbitrary entity metadata by name, via mc-data
	val onFire: Boolean get() = metadata[0]?.let { (it.value as Byte).and(1) != 0.toByte() } ?: false
	val crouching: Boolean get() = metadata[0]?.let { (it.value as Byte).and(2) != 0.toByte() } ?: false
	val invisible: Boolean get() = metadata[0]?.let { (it.value as Byte).and(0x20) != 0.toByte() } ?: false
	val glowing: Boolean get() = metadata[0]?.let { (it.value as Byte).and(0x40) != 0.toByte() } ?: false

	val air: Int get() = metadata[1]?.let { it.value as Int } ?: 300
	val potionEffectColor: Int get() = metadata[8]?.let { it.value as Int } ?: 300
	val additionalHearts: Float get() = metadata[11]?.let { it.value as Float } ?: 0f
}

interface ExpEntity : Entity {
	val exp: Int
}

interface MobEntity : Entity {
	val type: MobType
}

interface ObjectEntity : Entity {
	val type: ObjectType
	val data: ObjectData?
}

interface PaintingEntity : Entity {
	val paintingType: PaintingType
	val direction: HangingDirection
}

interface GlobalEntity : Entity {
	val type: GlobalEntityType
}

class Experience(val progress: Float, val level: Int, val total: Int)

interface PlayerEntity : Entity {
	val gameMode: GameMode?
	val food: Int?
	val saturation: Float?
	val experience: Experience?
	val playerListItem: PlayerListItem?

	val eyePos: Vec3d? get() = position?.plus(Vec3d(.0, eyeHeight, .0))
	val eyeHeight: Double get() = if (crouching) 1.27 else 1.62
}

interface PlayerListItem {
	val profile: GameProfile
	val displayName: Message?
	val gameMode: GameMode?
	val ping: Int?
	val entity: PlayerEntity?
}

interface World {
	val dimension: Int
	val chunkColumns: Map<ChunkPos, Column>
	val entities: Map<Int, Entity>

	val rainy: Boolean?
	val skyDarkness: Double?

	/**
	 * Get the [BlockState] at the [pos] (absolute world coordinates).
	 * @return null if not loaded or outside world
	 */
	fun getBlockState(pos: Vec3i): BlockState?
}
