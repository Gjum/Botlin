package com.github.gjum.minecraft.botlin.defaults

import com.github.gjum.minecraft.botlin.api.*

open class EventEmitterImpl<E : Event> : EventEmitter<E> {
    private val handlers = mutableMapOf<EventClass<E>,
        MutableCollection<EventHandler<*>>>()

    override fun <T : E> onEach(event: EventClass<T>, handler: EventHandler<T>): Unregister {
        handlers.getOrPut(event, ::mutableListOf)
            .add(handler)
        return { removeEventHandler(event, handler) }
    }

    override fun <T : E> removeEventHandler(event: EventClass<T>, handler: EventHandler<T>) {
        handlers[event]?.remove(handler as Any)
    }

    override fun <T : E> emit(event: T) {
        val evtHandlers = handlers[event.javaClass] ?: return
        for (handler in evtHandlers) {
            @Suppress("UNCHECKED_CAST")
            val theHandler = handler as EventHandler<T>
            try {
                theHandler.invoke(event)
            } catch (e: Exception) {
                e.printStackTrace()
                // TODO remove failed handler?
            }
        }
    }
}
