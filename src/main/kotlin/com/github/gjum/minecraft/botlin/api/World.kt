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
import com.github.steveice10.mc.protocol.data.message.Message
import java.util.UUID

/**
 * Properties that have not been received from the server yet are null.
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
	// TODO access mob metadata by name, via mc-data
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
}
