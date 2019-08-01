package com.github.gjum.minecraft.botlin.api

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.ReceiveChannel

/**
 * Emits events.
 * @see EventBoard for a source that is also an [EventSink].
 */
interface EventSource {
	fun <T> receiveAll(eventType: Class<T>): ReceiveChannel<T>
	suspend fun <T> receiveSingle(eventType: Class<T>): T
}

/**
 * Takes events and forwards them to listeners.
 * @see EventBoard for a sink that is also an [EventSource].
 */
interface EventSink {
	/**
	 * Returns immediately. Event is emitted later.
	 * The value inside the returned Deferred indicates whether
	 * the event was emitted (not cancelled).
	 */
	fun <T : Any> post(eventType: Class<T>, payload: T): Deferred<Boolean>

	/**
	 * Only calls [buildPayload] if the event has at least one listener.
	 * Returns immediately. Event is emitted later.
	 * The value inside the returned Deferred indicates whether
	 * the event was emitted (not cancelled).
	 */
	fun <T : Any> post(eventType: Class<T>, buildPayload: () -> T): Deferred<Boolean>
}

interface EventBoard : EventSink, EventSource

/**
 * TODO unused
 * Allows cancelling events before they are emitted.
 */
interface EventCanceller {
	fun shouldCancel(event: Any): Boolean
}

// shorthands that allow omitting the type token

inline fun <reified T : Any> EventSink.post(payload: T) = post(T::class.java, payload)

/**
 * Only calls [buildPayload] if the event has at least one listener.
 */
inline fun <reified T : Any> EventSink.post(noinline buildPayload: () -> T) = post(T::class.java, buildPayload)

inline fun <reified T : Any> EventSource.receiveAll() = receiveAll(T::class.java)

suspend inline fun <reified E : Any> EventSource.onEach(crossinline handler: suspend (E) -> Unit) {
	for (payload in receiveAll<E>()) {
		handler(payload)
	}
}

suspend inline fun <reified E : Any> EventSource.onEach(crossinline handler: (E) -> Unit) {
	for (payload in receiveAll<E>()) {
		handler(payload)
	}
}
