package com.github.gjum.minecraft.botlin.impl

import com.github.gjum.minecraft.botlin.api.*
import com.github.gjum.minecraft.botlin.behaviors.BlockPhysics
import com.github.gjum.minecraft.botlin.behaviors.ClientTicker
import com.github.gjum.minecraft.botlin.data.MinecraftData
import com.github.gjum.minecraft.botlin.util.AuthenticationProvider
import com.github.gjum.minecraft.botlin.util.race
import com.github.steveice10.mc.protocol.data.game.ClientRequest
import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand
import com.github.steveice10.mc.protocol.data.game.entity.player.InteractAction
import com.github.steveice10.mc.protocol.data.game.entity.player.PlayerAction
import com.github.steveice10.mc.protocol.data.game.entity.player.PlayerState
import com.github.steveice10.mc.protocol.data.game.window.*
import com.github.steveice10.mc.protocol.data.game.world.block.BlockFace
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientChatPacket
import com.github.steveice10.mc.protocol.packet.ingame.client.ClientRequestPacket
import com.github.steveice10.mc.protocol.packet.ingame.client.player.*
import com.github.steveice10.mc.protocol.packet.ingame.client.window.ClientCloseWindowPacket
import com.github.steveice10.mc.protocol.packet.ingame.client.window.ClientWindowActionPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.concurrent.fixedRateTimer
import kotlin.coroutines.coroutineContext

// TODO swing every 200 or 250 ms?
const val swingInterval: Long = 200 // ms

suspend fun setupClient(
	auth: AuthenticationProvider,
	eventBoard: EventBoardImpl
): MutableAvatar {
	val (proto, authError) = auth.authenticate()
	if (proto == null) throw IllegalArgumentException("Failed to setup Avatar: $authError", authError)
	val profile = proto.profile
	return MutableAvatar(profile, eventBoard)
}

suspend fun setupBot(
	username: String = "Botlin",
	extraBehaviors: List<(ApiBot) -> Behavior> = emptyList(),
	parentScopeArg: CoroutineScope? = null
): Bot {
	val parentScope = parentScopeArg ?: CoroutineScope(coroutineContext)
	val eventBoard = EventBoardImpl(parentScope)
	val auth = AuthenticationProvider(
		username,
		System.getProperty("mcAuthCredentials") ?: ".credentials",
		System.getProperty("mcAuthCache") ?: ".auth_tokens.json"
	)
	val avatar = setupClient(auth, eventBoard)
	val connection = ClientConnectionImpl(eventBoard, avatar, auth)
	val bot = MutableBot(avatar, eventBoard, connection, parentScope)
	for (behavior in extraBehaviors) {
		bot.registerBehavior(behavior(bot))
	}
	return bot
}

class MutableBot(
	override val avatar: MutableAvatar,
	private val eventBoard: EventBoard,
	private val connection: ClientConnectionImpl,
	coroutineScope: CoroutineScope
) : ChildScope(coroutineScope),
	ApiBot,
	Avatar by avatar,
	EventBoard by eventBoard,
	ClientConnection by connection {

	var actionId = 0
	val ticker = ClientTicker(this)
	val physics = BlockPhysics(this)
	private val behaviors = mutableListOf<Behavior>()

	override val mcData = MinecraftData("mcdata")

	fun registerBehavior(behavior: Behavior) {
		behaviors.add(behavior)
	}

	override fun unregisterBehavior(behavior: Behavior) {
		behaviors.remove(behavior)
	}

	override fun toString(): String {
		val connStr = if (connected) "on $serverAddress" else "(disconnected)"
		return "Bot(${profile.name} $connStr at ${playerEntity?.position})"
	}

	override suspend fun respawn() {
		sendPacket(ClientRequestPacket(ClientRequest.RESPAWN))
		avatar.playerEntity!!.reset()
		receiveSingle(AvatarEvent.Spawned::class.java)
	}

	override suspend fun chat(msg: String, skipQueue: Boolean): Int {
		// TODO split chat at spaces
		val chunks = msg.chunked(255)
		for (part in chunks) {
			sendPacket(ClientChatPacket(part))
			if (!skipQueue) delay(1000) // TODO chat queue
		}
		return chunks.size
	}

	override suspend fun lookAt(pos: Vec3d) = lookVec(pos - playerEntity!!.position!!)

	override suspend fun lookVec(vec: Vec3d) {
		avatar.playerEntity!!.look = avatar.playerEntity!!.look!!.turnToVec3(vec)
		// wait for look to be sent to server
		receiveSingle(AvatarEvent.ClientTick::class.java)
	}

	override suspend fun sneak(sneaking: Boolean) {
		val sneakState = if (sneaking) PlayerState.START_SNEAKING else PlayerState.STOP_SNEAKING
		sendPacket(ClientPlayerStatePacket(playerEntity!!.eid, sneakState))
	}

	override suspend fun sprint(sprinting: Boolean) {
		val sprintState = if (sprinting) PlayerState.START_SPRINTING else PlayerState.STOP_SPRINTING
		sendPacket(ClientPlayerStatePacket(playerEntity!!.eid, sprintState))
	}

	override suspend fun moveStraightTo(destination: Vec3d): Result<Route, MoveError> {
		return physics.moveStraightTo(destination)
	}

	override suspend fun jumpUntilLanded() = physics.jump()

	override suspend fun jumpToHeight(height: Double): Boolean = coroutineScope {
		val targetY = playerEntity!!.position!!.y + height
		race(
			async {
				receiveNext<AvatarEvent.ClientTick>() {
					playerEntity.position!!.y >= targetY
				}
				true // reached height
			},
			async {
				jumpUntilLanded()
				false // landed before reaching height
			}
		)
	}

	override suspend fun activateItem(hand: Hand) {
		sendPacket(ClientPlayerUseItemPacket(hand))
	}

	override suspend fun deactivateItem() {
		sendPacket(ClientPlayerActionPacket(
			PlayerAction.RELEASE_USE_ITEM,
			Position(0, 0, 0),
			BlockFace.DOWN))
	}

	override suspend fun selectHotbar(hotbarIndex: Int) {
		if (hotbarSelection == hotbarIndex) return // nothing to do
		sendPacket(ClientPlayerChangeHeldItemPacket(hotbarIndex))
		avatar.hotbarSelection = hotbarIndex
	}

	override suspend fun swapHands() {
		sendPacket(ClientPlayerActionPacket(
			PlayerAction.SWAP_HANDS,
			Position(0, 0, 0),
			BlockFace.DOWN))
	}

	/**
	 * Immediately updates [window] with [updateSlots] (optimistically)
	 * after sending the click packet.
	 * Waits for the corresponding [AvatarEvent.TransactionResponse] before returning.
	 * @throws ClickFailed
	 */
	private suspend fun sendClickAndAwaitResult(
		slot: Slot,
		action: WindowAction,
		param: WindowActionParam,
		updateSlots: () -> Unit
	) {
		val myWindowId = window!!.windowId
		val myActionId = ++actionId
		sendPacket(ClientWindowActionPacket(
			myWindowId, myActionId, slot.index, slot.toStack(), action, param))

		updateSlots()

		val response = receiveNext<AvatarEvent.TransactionResponse> {
			it.actionId == myActionId && it.windowId == myWindowId
		}
		if (!response.accepted) {
			throw ClickFailed("Click failed: ${action.javaClass.name}.${action.name}")
		}
	}

	private suspend fun sendClickAndAwaitResult(
		slotNr: Int, action: WindowAction, param: WindowActionParam, updateSlots: () -> Unit
	) = sendClickAndAwaitResult(avatar.window!!.slots[slotNr], action, param, updateSlots)

	private fun swapSlots(a: MutableSlot, b: MutableSlot) = avatar.window!!.apply {
		val slot = a.copy()
		a.updateFrom(b)
		b.updateFrom(slot)
	}

	private fun transferItems(from: MutableSlot, to: MutableSlot, amount: Int = 64) {
		val transferred = amount.coerceAtMost(from.amount)
			.coerceAtMost(to.maxStackSize - to.amount)
		from.amount -= transferred
		to.amount += transferred
	}

	/**
	 * Find the first slot this [slot] will be transferred to when shift clicked.
	 * First, tries to find a slot that stacks with [slot], is not full,
	 * and is in the opposite part of the window.
	 * If all these are exhausted, selects the next empty slot there.
	 */
	private fun findShiftClickDest(slot: Slot): MutableSlot? {
		val oppositeSlots = avatar.window!!.slots // TODO opposite slots
		return oppositeSlots.firstOrNull { !it.full && it.stacksWith(slot) }
			?: oppositeSlots.firstOrNull { it.empty }
	}

	override suspend fun swapHotbar(slotNr: Int, hbIndex: Int) {
		val param = MoveToHotbarParam.values()[hbIndex]
		sendClickAndAwaitResult(slotNr, WindowAction.MOVE_TO_HOTBAR_SLOT, param) {
			avatar.window!!.run { swapSlots(slots[slotNr], hotbar[hbIndex]) }
		}
	}

	override suspend fun clickSlot(slotNr: Int, right: Boolean, shift: Boolean) {
		val action = if (shift) WindowAction.SHIFT_CLICK_ITEM else WindowAction.CLICK_ITEM
		val param: WindowActionParam = if (shift) {
			if (right) ShiftClickItemParam.RIGHT_CLICK else ShiftClickItemParam.LEFT_CLICK
		} else {
			if (right) ClickItemParam.RIGHT_CLICK else ClickItemParam.LEFT_CLICK
		}
		sendClickAndAwaitResult(slotNr, action, param) {
			avatar.window!!.run {
				val clicked = slots[slotNr]
				if (shift) {
					while (!clicked.empty) {
						val destination = findShiftClickDest(clicked) ?: break
						transferItems(clicked, destination)
					}
				} else if (right) {
					transferItems(clicked, cursorSlot, amount = when {
						cursorSlot.empty -> clicked.amount - clicked.amount / 2 // bigger half
						clicked.full -> 0
						else -> -1
					})
				} else if (!cursorSlot.empty && cursorSlot.stacksWith(clicked)) {
					transferItems(cursorSlot, clicked)
				} else {
					swapSlots(clicked, cursorSlot)
				}
			}
		}
		TODO("clickSlot")
	}

	override suspend fun dropSlot(slotNr: Int, fullStack: Boolean) {
		val slot = avatar.window!!.run { if (slotNr < 0) cursorSlot else slots[slotNr] }
		val param = if (fullStack) DropItemParam.DROP_SELECTED_STACK else DropItemParam.DROP_FROM_SELECTED
		sendClickAndAwaitResult(slot, WindowAction.DROP_ITEM, param) {
			slot.amount -= if (fullStack) 64 else 1
			if (slot.empty) slot.itemId = 0
		}
	}

	override suspend fun dropHand(fullStack: Boolean) {
		val action = if (fullStack) PlayerAction.DROP_ITEM_STACK else PlayerAction.DROP_ITEM
		sendPacket(ClientPlayerActionPacket(
			action,
			Position(0, 0, 0),
			BlockFace.DOWN))
	}

	override suspend fun dragSlots(slotNrs: List<Int>, rmb: Boolean, shift: Boolean) {
		TODO("dragSlots")
	}

	private data class ScoredSlot(val slotNr: Int, val score: Int)

	override suspend fun holdBestItem(hand: Hand, itemScore: (Slot) -> Int) {
		val scoredSlots = window!!.slots.map { ScoredSlot(it.index, itemScore(it)) }
		val bestScore = scoredSlots.map { it.score }.max() ?: 0
		if (bestScore <= 0) {
			throw IllegalArgumentException("No matching slot found")
		}
		// find all slots that have the best score (all equally good)
		val bestSlots = scoredSlots.filter { it.score >= bestScore }

		// prefer slots from hotbar
		val bestHotbarSlot = bestSlots.firstOrNull { window.isInHotbar(it.slotNr) }
		if (bestHotbarSlot != null) {
			selectHotbar(bestHotbarSlot.slotNr - window.hotbarStart)
			return
		}

		// none found in hotbar, search full inventory
		val sourceNr = bestSlots.maxBy { it.score }?.slotNr
			?: error("Inventory changed while searching item to hold")

		val targetHb = if (mainHandSlot?.empty == true) {
			hotbarSelection!!
		} else {
			window.hotbar.firstOrNull { it.empty }?.index
				?: hotbarSelection!! // TODO if hotbar is full, use least recently used hotbar slot
		}

		// TODO handle cases when source is a restricted slot (armor, craft result, etc.) and target is not empty
		swapHotbar(sourceNr, targetHb)
		selectHotbar(targetHb)
	}

	override fun closeWindow() {
		window?.let { window ->
			sendPacket(ClientCloseWindowPacket(window.windowId))
			eventBoard.post(AvatarEvent.WindowClosed(window))
			avatar.window = null // XXX load player inventory? or is that stored separately?
		}
	}

	override suspend fun attackEntity(entity: Entity, hand: Hand, look: Boolean) {
		if (look) lookAtEntity(entity)
		sendPacket(ClientPlayerInteractEntityPacket(entity.eid, InteractAction.ATTACK, hand))
		sendPacket(ClientPlayerSwingArmPacket(hand))
	}

	override suspend fun interactEntity(entity: Entity, hand: Hand, look: Boolean) {
		if (look) lookAtEntity(entity)
		sendPacket(ClientPlayerInteractEntityPacket(entity.eid, InteractAction.INTERACT, hand))
		sendPacket(ClientPlayerSwingArmPacket(hand))
	}

	private suspend fun lookAtEntity(entity: Entity) {
		val eyeOffset = Vec3d.origin.withAxis(Axis.Y, 1.64)
		lookAt(entity.position!! + eyeOffset) // TODO if that's too far away, look at closest corner or look orthogonally at closest bounding box face
	}

	override suspend fun placeBlock(pos: Vec3d, face: BlockFace, hand: Hand, look: Boolean) {
		// TODO check reach or fail
		if (look) lookAt(pos)

		// TODO sneak+place+unsneak, so we never activate blocks
		// ... or make that optional so this method can be reused for activateBlock too

		val blockPos = pos.floored()
		val position = Position(blockPos.x, blockPos.y, blockPos.z)
		val cursor = pos - blockPos.asVec3d()
		sendPacket(ClientPlayerPlaceBlockPacket(position, face, hand,
			cursor.x.toFloat(), cursor.y.toFloat(), cursor.z.toFloat(),
			false)) // TODO insideBlock?

		sendPacket(ClientPlayerSwingArmPacket(hand))

		// TODO decrement count of slot item
	}

	override suspend fun activateBlock(pos: Vec3d, face: BlockFace, hand: Hand, look: Boolean) {
		// TODO check reach or fail
		if (look) lookAt(pos)
		val blockPos = pos.floored()
		val position = Position(blockPos.x, blockPos.y, blockPos.z)
		val cursor = pos - blockPos.asVec3d()
		// TODO unsneak?
		sendPacket(ClientPlayerPlaceBlockPacket(position, face, hand,
			cursor.x.toFloat(), cursor.y.toFloat(), cursor.z.toFloat(),
			false)) // TODO insideBlock?
		sendPacket(ClientPlayerSwingArmPacket(hand))
	}

	override suspend fun breakBlock(pos: Vec3d, face: BlockFace, breakMs: Long, look: Boolean) {
		val position = pos.floored().run { Position(x, y, z) }

		if (look) lookAt(pos)

		coroutineScope {
			// TODO use coroutine-based timer instead of thread-based
			val animation = fixedRateTimer(period = swingInterval) {
				sendPacket(ClientPlayerSwingArmPacket(Hand.MAIN_HAND))
			}
			try {
				sendPacket(ClientPlayerActionPacket(PlayerAction.START_DIGGING, position, face))

				delay(breakMs)

				sendPacket(ClientPlayerActionPacket(PlayerAction.FINISH_DIGGING, position, face))
			} catch (e: Throwable) { // and rethrow
				sendPacket(ClientPlayerActionPacket(PlayerAction.CANCEL_DIGGING, position, face))
				throw e
			} finally {
				animation.cancel()
			}
		}
	}
}
