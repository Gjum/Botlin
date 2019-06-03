package com.github.gjum.minecraft.botlin.api

interface AutoConnect : Service {
    /**
     * Automatically connect [username] to [serverAddress],
     * reconnecting it when disconnected with exponential backoff.
     */
    fun autoConnectTo(username: String, serverAddress: String?)

    /**
     * Returns all currently configured automatically connecting accounts
     * and which servers they connect to.
     */
    fun getCurrentlyAutoConnecting(): Map<String, String>

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
     * How many successful connections can be made before pausing.
     * Once there were [successes] successful connection attempts
     * in the past [intervalMs] milliseconds, no attempts will be made
     * until [intervalMs] after the [successes]-to-last attempt.
     */
    fun setSuccessRate(successes: Int, intervalMs: Int)
}
