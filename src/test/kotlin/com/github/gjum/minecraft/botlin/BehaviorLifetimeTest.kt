package com.github.gjum.minecraft.botlin

import com.github.gjum.minecraft.botlin.api.ChildScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Child(cs: CoroutineScope) : ChildScope(cs)
class Parent(cs: CoroutineScope) : ChildScope(cs) {
	val childA = Child(this)
	val childB = Child(this)
}

class BehaviorLifetimeTest {
	@Test
	fun subLifetimeTest() = runBlocking {
		val coroScope = this
		val sibling = Child(coroScope)
		val parent = Parent(coroScope)
		assertTrue(parent.isActive)
		assertTrue(parent.childA.isActive)
		assertTrue(parent.childB.isActive)
		assertTrue(sibling.isActive)

		// child closing should not kill parent/childB/sibling
		parent.childA.disable()
		assertTrue(parent.isActive)
		assertFalse(parent.childA.isActive)
		assertTrue(parent.childB.isActive)
		assertTrue(sibling.isActive)

		// parent closing should kill childB, but not sibling
		parent.disable()
		assertFalse(parent.isActive)
		assertFalse(parent.childA.isActive)
		assertFalse(parent.childB.isActive)
		assertTrue(sibling.isActive)

		// kill sibling to clean up
		sibling.disable()
		assertFalse(sibling.isActive)
	}
}
