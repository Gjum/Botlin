package com.github.gjum.minecraft.botlin.api

import kotlinx.coroutines.*

open class ChildScope(parentScope: CoroutineScope) : CoroutineScope by parentScope.makeChildScope() {

	fun disable() {
		coroutineContext.cancel()
	}
}

fun CoroutineScope.makeChildScope(): CoroutineScope {
	val childJob = Job(coroutineContext[Job])
	return CoroutineScope(newCoroutineContext(childJob))
}
