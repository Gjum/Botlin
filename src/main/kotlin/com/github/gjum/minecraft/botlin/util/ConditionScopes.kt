package com.github.gjum.minecraft.botlin.util

import com.github.gjum.minecraft.botlin.api.Event
import com.github.gjum.minecraft.botlin.api.EventEmitter
import com.github.gjum.minecraft.botlin.api.Result
import com.github.gjum.minecraft.botlin.api.Unregister
import kotlinx.coroutines.*
import kotlin.coroutines.resume

/**
 * Each time the [eventClass] occurs, calls [check].
 * The first time [check] returns true, this returns the event.
 */
suspend fun <E : Event> EventEmitter<in E>.awaitEventCondition(
	eventClass: Class<E>,
	check: (E) -> Boolean
): E {
	var continueWithEvent: ((E) -> Unit)? = null
	val seenEvents = mutableListOf<E>() // retain events seen before suspending the coroutine
	val unregister = onEach(eventClass) { event ->
		continueWithEvent?.invoke(event) ?: seenEvents.add(event)
	}
	try {
		return suspendCancellableCoroutine { cont ->
			continueWithEvent = {
				if (check(it)) cont.resume(it)
			}
			seenEvents.forEach { continueWithEvent!!(it) }
			seenEvents.clear()
		}
	} finally {
		unregister()
	}
}

/**
 * Waits for the event to occur and returns it.
 */
suspend fun <E : Event> EventEmitter<in E>.awaitEvent(eventClass: Class<E>): E {
	var unregister: Unregister? = null
	try {
		return suspendCancellableCoroutine { cont ->
			unregister = onEach(eventClass) { cont.resume(it) }
		}
	} finally {
		unregister?.invoke()
	}
}
