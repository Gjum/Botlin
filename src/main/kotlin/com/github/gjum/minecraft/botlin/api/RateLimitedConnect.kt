package com.github.gjum.minecraft.botlin.api

interface RateLimitedConnect : Service {
    /**
     * Tries to connect [avatar], using exponential backoff for unsuccessful attempts.
     * Calls [Avatar.useProtocol] on success.
     * May rely on [Authentication] service for authentication.
     */
    suspend fun connect(avatar: Avatar)

    /**
     * After getting disconnected from a successful connection,
     * wait [backoffStart] ms before attempting to reconnect.
     */
    var backoffStart: Int

    /**
     * Base of the exponential backoff. After each unsuccessful reconnect attempt,
     * multiply the wait time by [backoffFactor].
     */
    var backoffFactor: Float

    /**
     * Once the exponential backoff reaches [backoffEnd] ms, stop increasing the wait time.
     */
    var backoffEnd: Int

    /**
     * How many connections (successful or unsuccessful) can be made before pausing.
     * Once there were [limit] connection attempts
     * in the past [intervalMs] milliseconds, no attempts will be made
     * until [intervalMs] after the [limit]-to-last attempt.
     */
    fun limitConnectRate(limit: Int, intervalMs: Int)
}
