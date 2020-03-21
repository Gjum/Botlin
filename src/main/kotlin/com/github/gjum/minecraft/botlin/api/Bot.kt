package com.github.gjum.minecraft.botlin.api

import com.github.gjum.minecraft.botlin.data.MinecraftData
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand
import com.github.steveice10.mc.protocol.data.game.world.block.BlockFace

class ClickFailed(message: String) : RuntimeException(message)

/**
 * Control facade for scripting.
 */
interface Bot : Avatar, ClientConnection, EventSource<AvatarEvent> {
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

	// TODO double clicking slots
	suspend fun clickSlot(slotNr: Int, right: Boolean = false, shift: Boolean = false)

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
	 * @throws IllegalArgumentException if no matching items were found.
	 */
	suspend fun holdBestItem(hand: Hand = Hand.MAIN_HAND, itemScore: (Slot) -> Int): Result<Slot, String>

	/**
	 * Shorthand for [holdBestItem] with a boolean [predicate].
	 * If [predicate] is true, the [holdBestItem] score is 1, otherwise 0.
	 * @throws IllegalArgumentException if no matching items were found.
	 */
	suspend fun holdItem(hand: Hand = Hand.MAIN_HAND, predicate: (Slot) -> Boolean): Result<Slot, String> {
		return holdBestItem(hand) { if (predicate(it)) 1 else 0 }
	}

	fun closeWindow()

	suspend fun attackEntity(entity: Entity, hand: Hand = Hand.MAIN_HAND, look: Boolean = true)

	suspend fun interactEntity(entity: Entity, hand: Hand = Hand.MAIN_HAND, look: Boolean = true)

	/**
	 * Places a block onto [pos] from [face] using [hand].
	 * Sneaks for a tiny moment if necessary to prevent interacting with the block.
	 * [pos] can include sub-block resolution coordinates.
	 * @see activateBlock
	 */
	suspend fun placeBlock(pos: Vec3d, face: BlockFace = BlockFace.UP, hand: Hand = Hand.MAIN_HAND, look: Boolean = true)

	/**
	 * Shorthand for [placeBlock] with integer coordinates.
	 * See [closestBlockSubCoord] for how the float coords are determined.
	 */
	suspend fun placeBlock(
		pos: Vec3i,
		face: BlockFace = BlockFace.UP,
		hand: Hand = Hand.MAIN_HAND,
		look: Boolean = true
	) = placeBlock(closestBlockSubCoord(pos), face, hand, look)

	/**
	 * Activates (right-click) the block at [pos] from [face] using [hand].
	 * Un-sneaks for a tiny moment if necessary.
	 * [pos] can include sub-block resolution coordinates.
	 * @see placeBlock
	 */
	suspend fun activateBlock(pos: Vec3d, face: BlockFace = BlockFace.UP, hand: Hand = Hand.MAIN_HAND, look: Boolean = true)

	/**
	 * Shorthand for [activateBlock] with integer coordinates.
	 * @see closestBlockSubCoord for how the float coords are determined.
	 */
	suspend fun activateBlock(
		pos: Vec3i,
		face: BlockFace = BlockFace.UP,
		hand: Hand = Hand.MAIN_HAND,
		look: Boolean = true
	) = activateBlock(closestBlockSubCoord(pos), face, hand, look)

	/**
	 * Breaks the block at [pos] from [face], taking [breakMs].
	 * Cancel this coroutine to cancel the breaking.
	 * [pos] can include sub-block resolution coordinates.
	 */
	suspend fun breakBlock(
		pos: Vec3d,
		face: BlockFace = BlockFace.UP,
		breakMs: Long = 50,
		look: Boolean = true)

	/**
	 * Shorthand for [breakBlock] with integer coordinates.
	 * @see closestBlockSubCoord for how the float coords are determined.
	 */
	suspend fun breakBlock(
		pos: Vec3i,
		face: BlockFace = BlockFace.UP,
		breakMs: Long = 50,
		look: Boolean = true
	) = breakBlock(closestBlockSubCoord(pos), face, breakMs, look)

	private fun closestBlockSubCoord(pos: Vec3i) = pos.asVec3d + Vec3d(.5, .5, .5) // TODO click nearest face/edge

	// TODO start/cancel/finish digging - scoped?

	// TODO get stack from slot A to slot B
	// TODO craftRecipe

	// TODO useBucket multiple steps?
	// TODO placeSign (pos, lines, face)
	// TODO editBook(slot, pages)
	// TODO signBook(slot, author, title)
	// TODO writeBook(text, title="", sign=False/signWith=me)
}
