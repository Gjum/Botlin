package com.github.gjum.minecraft.botlin.api

import com.github.gjum.minecraft.botlin.impl.MutableAvatar
import kotlinx.coroutines.CoroutineScope

/**
 * [Bot] plus access to the [MutableAvatar], [CoroutineScope], and [EventSink].
 * [Behavior]s have access to this.
 */
interface ApiBot : Bot, CoroutineScope, EventBoard<AvatarEvent> {
	val avatar: MutableAvatar
	fun unregisterBehavior(behavior: Behavior)
}

open class Behavior(protected val bot: ApiBot) : ChildScope(bot) {
	override fun shutdown() {
		bot.unregisterBehavior(this)
		super.shutdown()
	}
}
