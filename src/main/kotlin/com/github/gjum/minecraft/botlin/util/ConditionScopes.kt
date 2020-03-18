package com.github.gjum.minecraft.botlin.util

import com.github.gjum.minecraft.botlin.api.EventBoard
import com.github.gjum.minecraft.botlin.api.Result
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.filter
import kotlin.coroutines.resume

/**
 * Await the [deferreds], returning the first result,
 * and finally cancelling them all.
 */
suspend fun <T> race(vararg deferreds: Deferred<T>): T = coroutineScope {
	val jobs = mutableListOf<Job>()
	try {
		suspendCancellableCoroutine<T> { cont ->
			deferreds.forEach { deferred ->
				jobs.add(launch(start = CoroutineStart.ATOMIC) {
					cont.resume(deferred.await())
				})
			}
		}
	} finally {
		jobs.forEach { it.cancel() }
		deferreds.forEach { it.cancel() }
	}
}

/**
 * Listens for [abortEvents] while running [block].
 * On any [abortEvents], cancels [block] and returns null.
 * To check for conditions on the events, use [ReceiveChannel.filter].
 */
suspend fun <R, E> EventBoard.duringInvariant(
	abortEvents: Collection<ReceiveChannel<out E>>,
	block: suspend () -> R
): Result<R, E> = coroutineScope {
	val ee = this@duringInvariant
	race(
		async { Result.Success<R, E>(block()) },
		*abortEvents.map { eventChannel ->
			async(start = CoroutineStart.ATOMIC) {
				val event = eventChannel.receive()
				Result.Failure<R, E>(event)
			}
		}.toTypedArray()
	)
}

/**
 * Listens for [events] while running [block].
 * On any [events], checks [invariant]; if it returns false, cancels [block] and returns null.
 */
suspend fun <R, E> EventBoard.duringInvariant(
	events: Collection<Class<out E>>, // TODO use stream of events instead of list?
	invariant: (E) -> Boolean,
	block: suspend () -> R
): Result<R, E> {
	val negatedInvariant = { e: E -> !invariant(e) }
	val abortEvents = events.map { e ->
		receiveAll(e).filter { negatedInvariant(it) }
	}
	return duringInvariant(abortEvents, block)
}
