@file:JvmName("JavaCallbackHelper")

package com.github.gjum.minecraft.botlin.util

import java.util.function.BiConsumer
import java.util.function.Consumer

fun <T> `$`(consumer: Consumer<T>): (T) -> Unit = consumer::accept
fun <T, U> `$`(consumer: BiConsumer<T, U>): (T, U) -> Unit = consumer::accept
fun <T, U, V> `$`(consumer: TriConsumer<T, U, V>): (T, U, V) -> Unit = consumer::accept
fun <T, U, V, W> `$`(consumer: FourConsumer<T, U, V, W>): (T, U, V, W) -> Unit = consumer::accept

interface TriConsumer<T, U, V> {
    fun accept(t: T, u: U, v: V)
}

interface FourConsumer<T, U, V, W> {
    fun accept(t: T, u: U, v: V, w: W)
}
