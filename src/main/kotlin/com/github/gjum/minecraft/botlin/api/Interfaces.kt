package com.github.gjum.minecraft.botlin.api

import com.github.gjum.minecraft.botlin.data.MinecraftData
import com.github.steveice10.mc.auth.data.GameProfile
import com.github.steveice10.mc.protocol.data.game.chunk.Column
import com.github.steveice10.mc.protocol.data.game.entity.metadata.EntityMetadata
import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand
import com.github.steveice10.mc.protocol.data.game.entity.type.GlobalEntityType
import com.github.steveice10.mc.protocol.data.game.entity.type.MobType
import com.github.steveice10.mc.protocol.data.game.entity.type.PaintingType
import com.github.steveice10.mc.protocol.data.game.entity.type.`object`.HangingDirection
import com.github.steveice10.mc.protocol.data.game.entity.type.`object`.ObjectData
import com.github.steveice10.mc.protocol.data.game.entity.type.`object`.ObjectType
import com.github.steveice10.mc.protocol.data.game.window.WindowType
import com.github.steveice10.mc.protocol.data.game.world.block.BlockFace
import com.github.steveice10.mc.protocol.data.message.Message
import com.github.steveice10.opennbt.tag.builtin.CompoundTag
import com.github.steveice10.packetlib.packet.Packet
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

interface Slot {
	val index: Int
	val itemId: Int
	val itemMeta: Int
	val amount: Int
	val nbtData: CompoundTag?
	val empty: Boolean
	val maxStackSize: Int
	val name: String
	val customName: String?
	fun stacksWith(other: Slot): Boolean
}

interface Window {
	val windowId: Int
	/** null means player window */
	val windowType: WindowType?
	val windowTitle: String?
	val ready: Boolean
	val slotCount: Int
	val slots: List<Slot>
	val cursorSlot: Slot
	val properties: Map<Int, Int>
	// TODO access window props by name, via mc-data
	val hotbar: List<Slot>
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

interface Avatar {
	val profile: GameProfile
	/** Normalized to format "host:port". */
	val serverAddress: String
	/**
	 * Kick/disconnect message, or null if still online.
	 */
	val endReason: String?

	/**
	 * Indicates if the account is logged into the server at this time.
	 */
	val connected: Boolean

	val playerEntity: PlayerEntity?
	val world: World?
	val playerList: Map<UUID, PlayerListItem>
	val window: Window?
	val hotbarSelection: Int?

	val mainHandSlot: Slot? get() = window?.hotbar?.get(hotbarSelection ?: 0)

	val identifier get() = "${profile.name}@$serverAddress"

	/**
	 * Indicates if the account has received all its state yet, such as
	 * position, health/food/exp, and is also [connected].
	 */
	val ingame: Boolean
		get() = (playerEntity?.position != null
			&& playerEntity?.health != null
			&& playerEntity?.experience != null
			&& world != null
			&& connected)

	/**
	 * Indicates if the account is alive and [ingame].
	 */
	val alive get() = (playerEntity?.health ?: 0.0f) > 0.0f && ingame
}

interface ClientConnection {
	val connected: Boolean

	/**
	 * Connects to the [Avatar.serverAddress].
	 * Waits until [AvatarEvent.Spawned], unless when [waitForSpawn] is false,
	 * in which case this only waits until the connection succeeded/failed.
	 * Throws an error if the connection failed.
	 */
	suspend fun connect(waitForSpawn: Boolean = true)

	fun disconnect(reason: String? = null, cause: Throwable? = null)

	fun sendPacket(packet: Packet)
}

/**
 * Control facade for scripting.
 */
interface Bot : Avatar, ClientConnection, EventSource {
	val mcData: MinecraftData

	suspend fun respawn()

	/**
	 * @param msg The message to send to the server. Should not contain newlines or other special characters. Should not exceed the server's message length limit (256).
	 * @param skipQueue If false (default), only one message is sent per second, as to not exceed a typical server-side rate limit. If true, sends the message immediately. In both cases, the message will count against the rate limit for future calls.
	 * @return number of messages in queue, including this one if it couldn't be sent immediately
	 */
	suspend fun chat(msg: String, skipQueue: Boolean = false): Int

	suspend fun lookAt(pos: Vec3d)

	suspend fun lookVec(vec: Vec3d)

	suspend fun sneak(sneaking: Boolean = true)

	suspend fun sprint(sprinting: Boolean = true)

	suspend fun moveStraightTo(destination: Vec3d): Result<Route, MoveError>

	suspend fun moveStraightBy(delta: Vec3d) = moveStraightTo(
		playerEntity!!.position!! + delta)

	/** Useful when jumping over a gap, or down a cliff. */
	suspend fun jumpUntilLanded()

	/**
	 * Starts a full jump, but returns as soon as that [height] is reached.
	 * Useful for jumping up a block or a staircase.
	 * [height] is relative to the avatar y coordinate when starting the jump.
	 *
	 * @return true if [height] was reached, false if landed before reaching height
	 */
	suspend fun jumpToHeight(height: Double): Boolean

	// TODO steer vehicle
	// TODO unmount vehicle
	// TODO jump vehicle/horse
	// TODO leave bed

	suspend fun activateItem(hand: Hand = Hand.MAIN_HAND)

	suspend fun deactivateItem()

	suspend fun selectHotbar(hotbarIndex: Int)

	/**
	 * Swaps the item stacks in the left and right hands.
	 */
	suspend fun swapHands()

	/**
	 * Swaps the slot [slotNr] with the hotbar slot [hbIndex].
	 * This is a dedicated action in the protocol and happens instantly,
	 * compared to the 2-3 [clickSlot] calls needed otherwise (for non-hotbar slots).
	 */
	suspend fun swapHotbar(slotNr: Int, hbIndex: Int)

	suspend fun clickSlot(slotNr: Int, rmb: Boolean = false, shift: Boolean = false)

	/**
	 * Drops items from [slotNr], or from the cursor slot if [slotNr] is negative.
	 */
	suspend fun dropSlot(slotNr: Int, fullStack: Boolean = false)

	/**
	 * Drops items from the main hand.
	 */
	suspend fun dropHand(fullStack: Boolean = false)

	suspend fun dragSlots(slotNrs: List<Int>, rmb: Boolean = false, shift: Boolean = false)

	/**
	 * Tries to hold a stack of a desired item in [hand].
	 * Of all slots in the inventory, the stack with the highest [itemScore] is used.
	 * Slots with [itemScore] of 0 or less will be ignored.
	 */
	suspend fun holdItem(itemScore: (Slot?) -> Int, hand: Hand = Hand.MAIN_HAND)

	fun closeWindow()

	suspend fun attackEntity(entity: Entity, hand: Hand = Hand.MAIN_HAND, look: Boolean = true)

	suspend fun interactEntity(entity: Entity, hand: Hand = Hand.MAIN_HAND, look: Boolean = true)

	/**
	 * Places a block onto [pos] from [face] using [hand].
	 * If necessary, un-sneaks for a tiny moment, to ensure placement.
	 * [pos] can include sub-block resolution coordinates.
	 * @see activateBlock
	 */
	suspend fun placeBlock(pos: Vec3d, face: BlockFace = BlockFace.UP, hand: Hand = Hand.MAIN_HAND, look: Boolean = true)

	/**
	 * Shorthand for [placeBlock].
	 * @see placeBlock
	 */
	suspend fun placeBlock(
		pos: Vec3i,
		face: BlockFace = BlockFace.UP,
		hand: Hand = Hand.MAIN_HAND,
		look: Boolean = true
	) = placeBlock(closestBlockSubcoord(pos), face, hand, look)

	/**
	 * Activates (right-click) the block at [pos] from [face] using [hand].
	 * Sneaks if a place-able item is in any hand, to prevent placement.
	 * [pos] can include sub-block resolution coordinates.
	 * @see placeBlock
	 */
	suspend fun activateBlock(pos: Vec3d, face: BlockFace = BlockFace.UP, hand: Hand = Hand.MAIN_HAND, look: Boolean = true)

	/**
	 * Shorthand for [activateBlock].
	 * @see activateBlock
	 */
	suspend fun activateBlock(
		pos: Vec3i,
		face: BlockFace = BlockFace.UP,
		hand: Hand = Hand.MAIN_HAND,
		look: Boolean = true
	) = activateBlock(closestBlockSubcoord(pos), face, hand, look)

	private fun closestBlockSubcoord(pos: Vec3i) = pos.asVec3d() + Vec3d(.5, .5, .5) // TODO click nearest face/edge

	/**
	 * Breaks the block at [pos] from [face], taking [breakMs].
	 * Cancel this coroutine to cancel the breaking.
	 * [pos] can include sub-block resolution coordinates.
	 */
	suspend fun breakBlock(pos: Vec3i, face: BlockFace = BlockFace.UP, breakMs: Long = 50, look: Boolean = true)

	/**
	 * Shorthand for [breakBlock].
	 * @see breakBlock
	 */
	suspend fun breakBlock(
		pos: Vec3d,
		face: BlockFace = BlockFace.UP,
		breakMs: Long = 50,
		look: Boolean = true
	) = breakBlock(pos.floored(), face, breakMs, look)

	// TODO start/cancel/finish digging - scoped?

	// TODO get stack from slot A to slot B
	// TODO craftRecipe

	// TODO useBucket multiple steps?
	// TODO placeSign (pos, lines, face)
	// TODO editBook(slot, pages)
	// TODO signBook(slot, author, title)
	// TODO writeBook(text, title="", sign=False/signWith=me)
}
