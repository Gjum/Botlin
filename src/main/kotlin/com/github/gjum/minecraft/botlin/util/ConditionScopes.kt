package com.github.gjum.minecraft.botlin.util

import com.github.gjum.minecraft.botlin.api.Event
import com.github.gjum.minecraft.botlin.api.EventEmitter
import com.github.gjum.minecraft.botlin.api.Unregister
import kotlinx.coroutines.*
import kotlin.coroutines.resume

/**
 * First, calls [check]. If it returns true, this returns null immediately.
 * Then, each time the [eventClass] occurs, calls [check].
 * The first time it returns true, this returns the event.
 */
suspend fun <E : Event> EventEmitter<in E>.awaitEventCondition(
	eventClass: Class<E>,
	check: (E?) -> Boolean
): E? {
	var unregister: Unregister? = null
	try {
		return suspendCancellableCoroutine { cont ->
			unregister = onEach(eventClass) { cont.resume(it) }

			if (check(null)) cont.resume(null)
		}
	} finally {
		unregister?.invoke()
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
