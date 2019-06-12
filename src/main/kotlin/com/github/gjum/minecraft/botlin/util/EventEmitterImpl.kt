package com.github.gjum.minecraft.botlin.util

import com.github.gjum.minecraft.botlin.api.Event
import com.github.gjum.minecraft.botlin.api.EventEmitter

open class EventEmitterImpl<E> : EventEmitter<E> {
    private val handlers = mutableMapOf<Event<*, E>,
        MutableCollection<Any>>()

    override fun <T> on(event: Event<T, E>, handler: T) {
        handlers.getOrPut(event, ::mutableListOf)
            .add(handler as Any)
    }

    override fun <T> removeEventHandler(event: Event<T, E>, handler: T) {
        handlers[event]?.remove(handler as Any)
    }

    override fun <T> emit(event: Event<T, E>, caller: (T) -> Unit) {
        val evtHandlers = handlers[event] ?: return
        for (handler in evtHandlers) {
            @Suppress("UNCHECKED_CAST")
            caller(handler as T)
        }
    }
}
