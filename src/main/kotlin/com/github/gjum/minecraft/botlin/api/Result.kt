package com.github.gjum.minecraft.botlin.api

sealed class Result<T, E> {
	open val value: T? get() = null
	open val error: E? get() = null

	class Success<T, E>(override val value: T) : Result<T, E>()
	class Failure<T, E>(override val error: E) : Result<T, E>()

	operator fun component1(): T? = value
	operator fun component2(): E? = error
}
