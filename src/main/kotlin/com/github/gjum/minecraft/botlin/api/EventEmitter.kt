package com.github.gjum.minecraft.botlin.api

interface Event

typealias EventClass<E> = Class<out E>
typealias EventHandler<E> = (E) -> Unit
typealias Unregister = () -> Unit

interface EventEmitter<E : Event> {
	fun <T : E> onEach(event: EventClass<T>, handler: EventHandler<T>): Unregister
	fun <T : E> removeEventHandler(event: EventClass<T>, handler: EventHandler<T>)
	fun <T : E> emit(event: T)
}
