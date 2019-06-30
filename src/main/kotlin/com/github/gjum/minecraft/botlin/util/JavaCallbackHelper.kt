@file:JvmName("JavaCallbackHelper")

package com.github.gjum.minecraft.botlin.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.function.BiConsumer
import java.util.function.Consumer
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

suspend inline fun <T> runOnThread(crossinline block: () -> T): T {
	var t: Thread? = null
	try {
		return suspendCancellableCoroutine { cont ->
			synchronized(cont) {
				t = thread(start = true) {
					synchronized(cont) {
						if (cont.isCancelled) return@thread
					}
					try {
						cont.resume(block())
					} catch (e: InterruptedException) {
						throw e
					} catch (e: Throwable) {
						cont.resumeWithException(e)
					}
				}
			}
		}
	} catch (e: CancellationException) {
		t?.interrupt()
		throw e
	}
}
