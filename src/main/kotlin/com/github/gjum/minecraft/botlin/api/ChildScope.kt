package com.github.gjum.minecraft.botlin.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.newCoroutineContext

open class ChildScope(parentScope: CoroutineScope) : CoroutineScope by parentScope.makeChildScope() {
	open fun shutdown() {
		coroutineContext.cancel()
	}
}

fun CoroutineScope.makeChildScope(): CoroutineScope {
	val childJob = Job(coroutineContext[Job])
	return CoroutineScope(newCoroutineContext(childJob))
}
