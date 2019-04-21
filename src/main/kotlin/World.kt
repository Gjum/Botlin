package com.github.gjum.minecraft.botlin

import com.github.steveice10.mc.auth.data.GameProfile
import com.github.steveice10.mc.protocol.data.game.entity.metadata.EntityMetadata
import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode
import com.github.steveice10.mc.protocol.data.game.entity.type.GlobalEntityType
import com.github.steveice10.mc.protocol.data.game.entity.type.MobType
import com.github.steveice10.mc.protocol.data.game.entity.type.PaintingType
import com.github.steveice10.mc.protocol.data.game.entity.type.`object`.HangingDirection
import com.github.steveice10.mc.protocol.data.game.entity.type.`object`.ObjectData
import com.github.steveice10.mc.protocol.data.game.entity.type.`object`.ObjectType
import com.github.steveice10.mc.protocol.data.message.Message
import com.github.steveice10.opennbt.NBTIO
import java.util.*

typealias BlockFace = Int

class Experience(val bar: Float, val level: Int, val total: Int)

class Slot(
    var blockId: Int,
    var itemCount: Int,
    var itemDamage: Int,
    var nbtData: NBTIO?
) {
    val empty: Boolean get() = blockId <= 0
    val name: String get() = "TODO" // XXX look up slot name in mc-data
    val customName: String? get() = "TODO" // XXX look up custom name in NBT
}

sealed class EntityType {
    class ExpOrb(val exp: Int) : EntityType()
    class Mob(val type: MobType) : EntityType()
    class Object(val type: ObjectType, val data: ObjectData?) : EntityType()
    class Painting(val paintingType: PaintingType, val direction: HangingDirection) : EntityType()
    object Player : EntityType()
    class Global(val type: GlobalEntityType) : EntityType()
}

class Entity(val eid: Int) {
    // these should be const but may not be known when creating the Entity instance
    var uuid: UUID? = null
    var type: EntityType? = null

    var position: Vec3d? = null
    var sneaking: Boolean? = null
    var look: Look? = null
    var headYaw: Double? = null
    var velocity: Vec3d? = null
    var onGround: Boolean? = null
    var passengers: Array<Entity> = emptyArray()
    var vehicle: Entity? = null
    var playerListItem: PlayerListItem? = null
    var metadata: Array<EntityMetadata>? = null

    val isPlayer: Boolean get() = playerListItem != null
    val eyePos: Vec3d? get() = position?.plus(Vec3d(.0, eyeHeight, .0))
    val eyeHeight: Double
        get() {
            return if (!isPlayer) TODO("entity eye height")
            else if (sneaking == true) TODO("sneaking player height")
            else 1.64
        }
}

class PlayerListItem(val profile: GameProfile) {
    var displayName: Message? = null
    var gameMode: GameMode? = null
    var ping: Int? = null
    var entity: Entity? = null
}

/**
 * Stores block info the bot has discovered so far.
 */
class World {
    // XXX
}

@ExperimentalUnsignedTypes
class BlockType(val id: UByte, val meta: UByte)

@ExperimentalUnsignedTypes
class Block(val type: BlockType) {
    var reinfLevel: ReinforcementLevel? = null
    var reinfGroup: String? = null
}

enum class ReinforcementLevel { Plain, Stone, Iron, Diamond }
