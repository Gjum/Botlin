package com.github.gjum.minecraft.botlin

import com.github.gjum.minecraft.botlin.api.ChildScope
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.coroutines.resumeWithException

class ChildScopeTest {
	@Test
	fun `sub lifetime`() = runBlocking {
		class Child(cs: CoroutineScope) : ChildScope(cs)
		class Parent(cs: CoroutineScope) : ChildScope(cs) {
			val childA = Child(this@Parent)
			val childB = Child(this@Parent)
		}

		val coroutineScope = this@runBlocking
		val sibling = Child(coroutineScope)
		val parent = Parent(coroutineScope)
		Assertions.assertTrue(parent.isActive)
		Assertions.assertTrue(parent.childA.isActive)
		Assertions.assertTrue(parent.childB.isActive)
		Assertions.assertTrue(sibling.isActive)

		// child closing should not kill parent/childB/sibling
		parent.childA.disable()
		Assertions.assertTrue(parent.isActive)
		Assertions.assertFalse(parent.childA.isActive)
		Assertions.assertTrue(parent.childB.isActive)
		Assertions.assertTrue(sibling.isActive)

		// parent closing should kill childB, but not sibling
		parent.disable()
		Assertions.assertFalse(parent.isActive)
		Assertions.assertFalse(parent.childA.isActive)
		Assertions.assertFalse(parent.childB.isActive)
		Assertions.assertTrue(sibling.isActive)

		// kill sibling to clean up
		sibling.disable()
		Assertions.assertFalse(sibling.isActive)
	}

	@Test
	fun `on child launch cancellation`() = runBlocking {
		val parentScope = this@runBlocking
		val child = ChildWithSubJobs(parentScope)
		yield() // allow child's jobs to start

		Assertions.assertTrue(child.isActive)

		child.launchCont!!.cancel()
		yield() // allow cancellation to happen

		Assertions.assertTrue(child.isActive)
		Assertions.assertTrue(child.asyncDeferred.isActive)
		Assertions.assertFalse(child.launchJob.isActive)

		// kill child to clean up
		child.disable()
		Assertions.assertFalse(child.isActive)
	}

	@Test
	fun `on child async cancellation`() = runBlocking {
		val parentScope = this@runBlocking
		val child = ChildWithSubJobs(parentScope)
		yield() // allow child's jobs to start

		Assertions.assertTrue(child.isActive)
		Assertions.assertTrue(child.asyncDeferred.isActive)
		Assertions.assertTrue(child.launchJob.isActive)

		child.asyncCont!!.cancel()
		yield() // allow cancellation to happen

		Assertions.assertTrue(child.isActive)
		Assertions.assertTrue(child.launchJob.isActive)
		Assertions.assertFalse(child.asyncDeferred.isActive)

		// kill child to clean up
		child.disable()
		Assertions.assertFalse(child.isActive)
	}

	@Test
	fun `on child launch error`() {
		class TestException : Exception()
		Assertions.assertThrows(TestException::class.java) {
			runBlocking {
				val parentScope = this@runBlocking
				val child = ChildWithSubJobs(parentScope)
				yield() // allow child's jobs to start

				Assertions.assertTrue(child.isActive)
				Assertions.assertTrue(child.asyncDeferred.isActive)
				Assertions.assertTrue(child.launchJob.isActive)

				try {
					child.launchCont!!.resumeWithException(TestException())
					yield() // allow cancellation to propagate

					Assertions.assertFalse(child.launchJob.isActive)
					Assertions.assertFalse(child.asyncDeferred.isActive)
					Assertions.assertFalse(child.isActive)

					Assertions.fail<Unit>("Did not throw an exception")
				} catch (e: Exception) {
//					Assertions.assertEquals("oops", e.message)
					Assertions.fail<Unit>("caught exception, should've cancelled the whole scope")
				}
			}
		}
	}

	@Test
	fun `on child async error`() {
		class TestException : Exception()
		Assertions.assertThrows(TestException::class.java) {
			runBlocking {
				val parentScope = this@runBlocking
				val child = ChildWithSubJobs(parentScope)
				yield() // allow child's jobs to start

				Assertions.assertTrue(child.isActive)
				Assertions.assertTrue(child.asyncDeferred.isActive)
				Assertions.assertTrue(child.launchJob.isActive)

				try {
					child.asyncCont!!.resumeWithException(TestException())
					yield() // allow cancellation to propagate

					Assertions.assertFalse(child.asyncDeferred.isActive)
					Assertions.assertFalse(child.launchJob.isActive)
					Assertions.assertFalse(child.isActive)

					Assertions.fail<Unit>("Did not throw an exception")
				} catch (e: Exception) {
//					Assertions.assertEquals("oops", e.message)
					Assertions.fail<Unit>("caught exception, should've cancelled the whole scope")
				}
			}
		}
	}
}

private class ChildWithSubJobs(parent: CoroutineScope) : ChildScope(parent) {
	var launchCont: CancellableContinuation<Unit>? = null
	var asyncCont: CancellableContinuation<String>? = null
	var launchJob: Job
	var asyncDeferred: Deferred<String>

	init {
		launchJob = launch { suspendCancellableCoroutine<Unit> { launchCont = it } }
		asyncDeferred = async { suspendCancellableCoroutine<String> { asyncCont = it } }
	}
}
