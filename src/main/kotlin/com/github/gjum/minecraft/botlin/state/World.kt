package com.github.gjum.minecraft.botlin.state

import com.github.gjum.minecraft.botlin.util.Look
import com.github.gjum.minecraft.botlin.util.Vec3d
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
import com.github.steveice10.mc.protocol.data.game.world.block.value.BlockValue
import com.github.steveice10.mc.protocol.data.game.world.block.value.BlockValueType
import com.github.steveice10.mc.protocol.data.message.Message
import java.util.*

class Experience(val progress: Float, val level: Int, val total: Int)

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

    fun updateMetadata(metadata: Array<EntityMetadata>) {
        this.metadata = metadata // XXX update, don't just set!
    }
}

class PlayerListItem(val profile: GameProfile) {
    var displayName: Message? = null
    var gameMode: GameMode? = null
    var ping: Int? = null
    var entity: Entity? = null
}

private val Int.div16 get() = this shr 4
private val Int.mod16 get() = this and 0xf

data class ChunkPos(val x: Int, val z: Int) {
    companion object {
        fun fromBlock(x: Int, z: Int) = ChunkPos(x.div16, z.div16)
    }
}

class World(val dimension: Int) {
    private val chunkColumns: MutableMap<ChunkPos, Column> = mutableMapOf()
    val entities = mutableMapOf<Int, Entity>()
    var rainy: Boolean? = null
    var skyDarkness: Double? = null

    fun getBlock(x: Int, y: Int, z: Int): BlockState? {
        return getSection(x, y, z)?.blocks?.get(x.mod16, y.mod16, z.mod16)
    }

    fun getBlockData(x: Int, y: Int, z: Int) {
        TODO("getBlockData")
    }

    private fun getColumn(x: Int, z: Int): Column? {
        return chunkColumns[ChunkPos.fromBlock(x, z)]
    }

    private fun getSection(x: Int, y: Int, z: Int): Chunk? {
        return getColumn(x, z)?.chunks?.get(y.div16)
    }

    fun setBlock(position: Position, block: BlockState) {
        position.apply {
            val section = getSection(x, y, z) ?: return
            section.blocks.set(x.mod16, y.mod16, z.mod16, block)
        }
    }

    fun setBlockData(position: Position, blockId: Int, type: BlockValueType, value: BlockValue) {
        val column = getColumn(position.x, position.z) ?: return
        // TODO column.tileEntities should be a coord-indexed map...
    }

    fun unloadColumn(x: Int, z: Int) {
        chunkColumns.remove(ChunkPos(x, z))
    }

    fun updateColumn(x: Int, z: Int, column: Column) {
        // TODO apply partial updates - i.e., when sections are missing
        chunkColumns[ChunkPos(x, z)] = column
    }
}
