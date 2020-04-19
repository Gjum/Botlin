package com.github.gjum.minecraft.botlin.impl

import com.github.gjum.minecraft.botlin.api.ChildScope
import com.github.gjum.minecraft.botlin.api.EventBoard
import com.github.gjum.minecraft.botlin.api.EventCanceller
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel

class EventBoardImpl<T>(
	parentScope: CoroutineScope = GlobalScope
) : EventBoard<T>,
	ChildScope(parentScope) {

	// private val cancellers = mutableMapOf<Class<*>, MutableCollection<EventCanceller>>()
	private val handlers = mutableMapOf<Class<*>, MutableCollection<SendChannel<*>>>()

	override fun <E : T> postAsync(eventType: Class<E>, payload: E): Deferred<Boolean> {
		val handlersForEvent = synchronized(this) {
			handlers[eventType]
				?: return CompletableDeferred(true)
		}
		// emit later, allowing handlers registered after invoking this function to receive this payload
		return this.async {
			@Suppress("UNCHECKED_CAST")
			val handlersT = handlersForEvent as Collection<SendChannel<T>>
//			val cancellersT = synchronized(this) {
//				@Suppress("UNCHECKED_CAST")
//				cancellers[eventType] as Collection<EventCanceller>?
//			}
			var cancelledBy: EventCanceller<T>? = null
//			if (cancellersT != null) {
//				for (canceller in cancellersT) {
//					if (canceller.shouldCancel(payload)) {
//						cancelledBy = canceller
//					}
//				}
//			}
			if (cancelledBy == null) {
				for (channel in handlersT) {
					launch {
						channel.send(payload)
					}
				}
			}
			cancelledBy != null
		}
	}

	override fun <E : T> postAsync(eventType: Class<E>, buildPayload: () -> E): Deferred<Boolean> {
		if (eventType !in handlers) return CompletableDeferred(true)
		return postAsync(eventType, buildPayload())
	}

	override fun <E : T> receiveAll(eventType: Class<E>): ReceiveChannel<E> {
		val channel = Channel<E>()
		channel.invokeOnClose {
			synchronized(this) {
				handlers[eventType]?.also { handlersForEvent ->
					handlersForEvent.remove(channel)
					if (handlersForEvent.isEmpty()) handlers.remove(eventType)
				}
			}
		}
		synchronized(this) {
			val handlersForEvent = handlers.computeIfAbsent(eventType) { mutableListOf() }
			handlersForEvent.add(channel)
		}
		return channel
	}

	override suspend fun <E : T> receiveSingle(eventType: Class<E>): E {
		val chan = receiveAll(eventType)
		val payload = chan.receive()
		chan.cancel()
		return payload
	}
}
