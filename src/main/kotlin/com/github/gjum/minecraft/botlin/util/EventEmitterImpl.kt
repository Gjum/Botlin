package com.github.gjum.minecraft.botlin.util

import com.github.gjum.minecraft.botlin.api.Event
import com.github.gjum.minecraft.botlin.api.EventClass
import com.github.gjum.minecraft.botlin.api.EventEmitter
import com.github.gjum.minecraft.botlin.api.EventHandler

open class EventEmitterImpl<E : Event> : EventEmitter<E> {
    private val handlers = mutableMapOf<EventClass<E>,
        MutableCollection<EventHandler<*>>>()

    override fun <T : E> on(event: EventClass<T>, handler: EventHandler<T>) {
        handlers.getOrPut(event, ::mutableListOf)
            .add(handler)
    }

    override fun <T : E> removeEventHandler(event: EventClass<T>, handler: EventHandler<T>) {
        handlers[event]?.remove(handler as Any)
    }

    override fun <T : E> emit(event: T) {
        val evtHandlers = handlers[event.javaClass] ?: return
        for (handler in evtHandlers) {
            @Suppress("UNCHECKED_CAST")
            val theHandler = handler as EventHandler<T>
            theHandler.invoke(event)
        }
    }
}
