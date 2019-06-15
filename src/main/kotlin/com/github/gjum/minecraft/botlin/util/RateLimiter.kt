package com.github.gjum.minecraft.botlin.util

/** For testing. Gets inlined by JVM JIT (hopefully). */
internal object TimeProxy {
    fun currentTimeMillis() = System.currentTimeMillis()
    suspend fun delay(ms: Long) = kotlinx.coroutines.delay(ms)
}

class RateLimiter(private val parent: Params) {
    private val rateQueue = mutableListOf<Long>()
    private var nextBackoff: Float = parent.backoffStart.toFloat() / parent.backoffFactor
    private var prevAttempt = 0L // at most now - backoffStart / backoffFactor

    interface Params {
        val backoffStart: Int
        val backoffFactor: Float
        val backoffEnd: Int
        val connectRateLimit: Int
        val connectRateInterval: Int
    }

    @Synchronized
    suspend fun <T> runWithRateLimit(block: suspend () -> Pair<T, Boolean>): T {
        val callTime = TimeProxy.currentTimeMillis()

        // remove unused entries
        while (rateQueue.isNotEmpty() && rateQueue[0] < callTime - parent.connectRateInterval) rateQueue.removeAt(0)
        // apply success rate limit
        val nextRateSlot = if (rateQueue.size >= parent.connectRateLimit) {
            rateQueue[0] + parent.connectRateInterval
        } else callTime

        val nextAttempt = prevAttempt + nextBackoff.toLong()
        val waitMs = java.lang.Long.max(nextAttempt - callTime, nextRateSlot - callTime)
        if (waitMs > 0) TimeProxy.delay(waitMs)

        prevAttempt = TimeProxy.currentTimeMillis()
        val (result, successful) = block()

        if (successful) {
            rateQueue.add(prevAttempt)
            nextBackoff = parent.backoffStart.toFloat()
        } else {
            nextBackoff *= parent.backoffFactor
        }
        if (nextBackoff > parent.backoffEnd) nextBackoff = parent.backoffEnd.toFloat()

        return result
    }
}

suspend fun <T> RateLimiter.runWithRateLimitWhereNonNullIsSuccess(block: suspend () -> T): T {
    return runWithRateLimit {
        val result = block()
        result to (result != null)
    }
}
