package com.github.gjum.minecraft.botlin.impl

import com.github.gjum.minecraft.botlin.api.*
import com.github.gjum.minecraft.botlin.behaviors.BlockPhysics
import com.github.gjum.minecraft.botlin.behaviors.ClientTicker
import com.github.gjum.minecraft.botlin.data.MinecraftData
import com.github.gjum.minecraft.botlin.util.AuthenticationProvider
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand
import com.github.steveice10.mc.protocol.data.game.world.block.BlockFace
import com.github.steveice10.mc.protocol.packet.ingame.client.window.ClientCloseWindowPacket
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.coroutineContext

suspend fun setupClient(
	serverAddress: String = "localhost:25565",
	auth: AuthenticationProvider,
	eventBoard: EventBoardImpl
): MutableAvatar {
	val (proto, authError) = auth.authenticate()
	if (proto == null) throw IllegalArgumentException("Failed to setup Avatar: $authError", authError)
	val profile = proto.profile
	return MutableAvatar(profile, serverAddress, eventBoard)
}

suspend fun setupBot(
	username: String = "Botlin",
	serverAddress: String = "localhost:25565",
	parentScopeArg: CoroutineScope? = null
): Bot {
	val parentScope = parentScopeArg ?: CoroutineScope(coroutineContext)
	val eventBoard = EventBoardImpl(parentScope)
	val auth = AuthenticationProvider(
		username,
		System.getProperty("mcAuthCredentials") ?: ".credentials",
		System.getProperty("mcAuthCache") ?: ".auth_tokens.json"
	)
	val avatar = setupClient(serverAddress, auth, eventBoard)
	val connection = ClientConnectionImpl(eventBoard, avatar, auth)
	return MutableBot(avatar, eventBoard, connection, parentScope)
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

	val ticker = ClientTicker(this)
	val physics = BlockPhysics(this)

	override val mcData = MinecraftData("mcdata")

	override val connected get() = connection.connected && avatar.connected

	override suspend fun respawn() {
		TODO("respawn")
	}

	override suspend fun chat(msg: String, skipQueue: Boolean): Int {
		TODO("chat")
	}

	override suspend fun lookAt(pos: Vec3d) {
		TODO("lookAt")
	}

	override suspend fun lookVec(vec: Vec3d) {
		TODO("lookVec")
	}

	override suspend fun sneak(sneaking: Boolean) {
		TODO("sneak")
	}

	override suspend fun sprint(sprinting: Boolean) {
		TODO("sprint")
	}

	override suspend fun moveStraightTo(destination: Vec3d): Result<Route, MoveError> {
		TODO("moveStraightTo")
	}

	override suspend fun jumpUntilLanded() {
		TODO("jumpUntilLanded")
	}

	override suspend fun jumpToHeight(height: Double): Boolean {
		TODO("jumpToHeight")
	}

	override suspend fun activateItem(hand: Hand) {
		TODO("activateItem")
	}

	override suspend fun deactivateItem() {
		TODO("deactivateItem")
	}

	override suspend fun selectHotbar(hotbarIndex: Int) {
		TODO("selectHotbar")
	}

	override suspend fun swapHands() {
		TODO("swapHands")
	}

	override suspend fun swapHotbar(slotNr: Int, hbIndex: Int) {
		TODO("swapHotbar")
	}

	override suspend fun clickSlot(slotNr: Int, rmb: Boolean, shift: Boolean) {
		TODO("clickSlot")
	}

	override suspend fun dropSlot(slotNr: Int, fullStack: Boolean) {
		TODO("dropSlot")
	}

	override suspend fun dropHand(fullStack: Boolean) {
		TODO("dropHand")
	}

	override suspend fun dragSlots(slotNrs: List<Int>, rmb: Boolean, shift: Boolean) {
		TODO("dragSlots")
	}

	override suspend fun holdItem(itemScore: (Slot?) -> Int, hand: Hand) {
		TODO("holdItem")
	}

	override fun closeWindow() {
		window?.let { window ->
			sendPacket(ClientCloseWindowPacket(window.windowId))
			eventBoard.post(AvatarEvent.WindowClosed(window))
			avatar.window = null // XXX load player inventory? or is that stored separately?
		}
	}

	override suspend fun attackEntity(entity: Entity, hand: Hand, look: Boolean) {
		TODO("attackEntity")
	}

	override suspend fun interactEntity(entity: Entity, hand: Hand, look: Boolean) {
		TODO("interactEntity")
	}

	override suspend fun placeBlock(pos: Vec3d, face: BlockFace, hand: Hand, look: Boolean) {
		TODO("placeBlock")
	}

	override suspend fun activateBlock(pos: Vec3d, face: BlockFace, hand: Hand, look: Boolean) {
		TODO("activateBlock")
	}

	override suspend fun breakBlock(pos: Vec3i, face: BlockFace, breakMs: Long, look: Boolean) {
		TODO("breakBlock")
	}
}
