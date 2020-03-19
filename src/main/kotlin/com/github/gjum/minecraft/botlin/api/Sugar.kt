package com.github.gjum.minecraft.botlin.api

import com.github.gjum.minecraft.botlin.data.BlockStateInfo
import com.github.gjum.minecraft.botlin.impl.ClientConnectionImpl
import com.github.gjum.minecraft.botlin.impl.EventBoardImpl
import com.github.gjum.minecraft.botlin.impl.MutableAvatar
import com.github.gjum.minecraft.botlin.impl.MutableBot
import com.github.gjum.minecraft.botlin.util.AuthenticationProvider
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.coroutineContext

val NORTH = Cardinal.NORTH.asVec3i // Vec3d(0.0, 0.0, -1.0)
val SOUTH = Cardinal.SOUTH.asVec3i // Vec3d(0.0, 0.0, 1.0)
val WEST = Cardinal.WEST.asVec3i // Vec3d(-1.0, 0.0, 0.0)
val EAST = Cardinal.EAST.asVec3i // Vec3d(1.0, 0.0, 0.0)
val DOWN = Cardinal.DOWN.asVec3i // Vec3d(0.0, -1.0, 0.0)
val UP = Cardinal.UP.asVec3i // Vec3d(0.0, 1.0, 0.0)

val Bot.forward get() = playerEntity!!.look!!
val Bot.backward get() = forward.run { Look(yaw + Math.PI, pitch) }
val Bot.feet get() = playerEntity!!.position!!
val Bot.inFront get() = feet + forward.asVec3d
val Bot.behind get() = feet + backward.asVec3d

fun Bot.isBlock(stack: Stack) = mcData.items[stack.itemId]?.block != null

val BlockStateInfo.isSolid get() = collisionShape.boxes.isNotEmpty()

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

private data class ScoredSlot(val slot: Slot, val score: Int)

fun Bot.findBestSlot(itemScore: (Slot) -> Int): Slot? {
	val scoredSlots = window!!.slots.map { ScoredSlot(it, itemScore(it)) }
	val bestScore = scoredSlots.map { it.score }.max() ?: 0
	if (bestScore <= 0) {
		return null // no matching slot found
	}
	// find all slots that have the best score (all equally good)
	val bestSlots = scoredSlots.filter { it.score >= bestScore }

	// prefer slots from hotbar
	return bestSlots.firstOrNull { window!!.isInHotbar(it.slot.index) }?.slot
		?: bestSlots.maxBy { it.score }?.slot
}

fun Bot.findSlot(predicate: (Slot) -> Boolean): Slot? {
	return findBestSlot { if (predicate(it)) 1 else 0 }
}
