package com.github.gjum.minecraft.botlin.api

interface EventEmitter<E> {
    fun <T> on(event: Event<T, E>, handler: T)
    fun <T> removeEventHandler(event: Event<T, E>, handler: T)
    fun <T> emit(event: Event<T, E>, caller: (T) -> Unit)
}
