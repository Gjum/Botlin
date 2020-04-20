package com.github.gjum.minecraft.botlin.api

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.first

// In this file, E denotes a concrete event, and T denotes its parent event class.

/**
 * Emits events.
 * @see EventBoard for a source that is also an [EventSink].
 */
interface EventSource<T> {
	fun <E : T> receiveAll(eventType: Class<E>): ReceiveChannel<E>
	suspend fun <E : T> receiveSingle(eventType: Class<E>): E
}

/**
 * Takes events and forwards them to listeners.
 * @see EventBoard for a sink that is also an [EventSource].
 */
interface EventSink<T> {
	/**
	 * Returns immediately. Event is emitted later.
	 * The value inside the returned Deferred indicates whether
	 * the event was emitted (not cancelled).
	 */
	fun <E : T> postAsync(eventType: Class<E>, payload: E): Deferred<Boolean>

	/**
	 * Only calls [buildPayload] if the event has at least one listener.
	 * Returns immediately. Event is emitted later.
	 * The value inside the returned Deferred indicates whether
	 * the event was emitted (not cancelled).
	 */
	fun <E : T> postAsync(eventType: Class<E>, buildPayload: () -> E): Deferred<Boolean>

	fun shutdown()
}

interface EventBoard<T> : EventSink<T>, EventSource<T>

/**
 * TODO unused
 * Allows cancelling events before they are emitted.
 */
interface EventCanceller<T> {
	fun shouldCancel(event: T): Boolean
}

// shorthands that allow omitting the type token

inline fun <reified E> EventSink<in E>.postAsync(payload: E) = postAsync(E::class.java, payload)

/**
 * Only calls [buildPayload] if the event has at least one listener.
 */
inline fun <reified E> EventSink<in E>.postAsync(noinline buildPayload: () -> E) = postAsync(E::class.java, buildPayload)

inline fun <reified E> EventSource<in E>.receiveAll() = receiveAll(E::class.java)

suspend inline fun <reified E> EventSource<in E>.receiveNext(predicate: (E) -> Boolean) = receiveAll(E::class.java).first(predicate)

suspend inline fun <reified E> EventSource<in E>.onEachSuspend(crossinline handler: suspend (E) -> Unit) {
	for (payload in receiveAll()) {
		handler(payload)
	}
}

suspend inline fun <reified E> EventSource<in E>.onEach(crossinline handler: (E) -> Unit) {
	for (payload in receiveAll()) {
		handler(payload)
	}
}
