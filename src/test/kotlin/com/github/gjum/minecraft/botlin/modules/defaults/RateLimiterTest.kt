package com.github.gjum.minecraft.botlin.modules.defaults

import com.github.gjum.minecraft.botlin.util.RateLimiter
import com.github.gjum.minecraft.botlin.util.TimeProxy
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Test

class RateLimiterTest {
    @Test
    fun `backoff gets applied and reset`() {
        mockkObject(TimeProxy)
        var nowMs = 1560000000L
        every { TimeProxy.currentTimeMillis() } answers { nowMs }
        val delayMs = slot<Long>()
        coEvery { TimeProxy.delay(capture(delayMs)) } answers {
            nowMs += delayMs.captured
        }

        val params = object : RateLimiter.Params {
            override val backoffStart = 1000
            override val backoffFactor = 2f
            override val backoffEnd = 5_000
            override val connectRateLimit = 5
            override val connectRateInterval = 60_000
        }
        val rateLimiter = spyk(RateLimiter(params))

        var success = false
        val getSuccess = spyk<() -> Boolean>(fun() = success)
        val block = suspend { (0 to getSuccess()) }

        runBlocking {
            // failed attempts use exponential backoff
            success = false
            // attempt 1: no delay
            rateLimiter.runWithRateLimit(block)
            // attempt 2: after backoffStart
            rateLimiter.runWithRateLimit(block)
            // attempt 3: after backoffStart * 2
            rateLimiter.runWithRateLimit(block)
            // attempt 4: after backoffStart * 2 * 2
            rateLimiter.runWithRateLimit(block)
            // attempt n: after backoffEnd
            rateLimiter.runWithRateLimit(block)
            // attempt n+1: also after backoffEnd
            // also set up success for next part ...
            success = true
            rateLimiter.runWithRateLimit(block)
            // successful attempt resets backoff to backoffStart
            rateLimiter.runWithRateLimit(block)
            // repeated successful attempts delayed by backoffStart
            // also setup failure for next part ...
            success = false
            rateLimiter.runWithRateLimit(block)
            // after success, failures use exponential backoff again
            rateLimiter.runWithRateLimit(block)
        }

        coVerifyOrder {
            // delay(0) is not called
            TimeProxy.delay(1000)
            TimeProxy.delay(2000)
            TimeProxy.delay(4000)
            TimeProxy.delay(5000)
            TimeProxy.delay(5000)
            TimeProxy.delay(1000)
            TimeProxy.delay(1000)
            TimeProxy.delay(2000)
        }
        coVerifyOrder {
            rateLimiter.runWithRateLimit(block)
            getSuccess()
            rateLimiter.runWithRateLimit(block)
            getSuccess()
            rateLimiter.runWithRateLimit(block)
            getSuccess()
            rateLimiter.runWithRateLimit(block)
            getSuccess()
            rateLimiter.runWithRateLimit(block)
            getSuccess()
            rateLimiter.runWithRateLimit(block)
            getSuccess()
            rateLimiter.runWithRateLimit(block)
            getSuccess()
            rateLimiter.runWithRateLimit(block)
            getSuccess()
            rateLimiter.runWithRateLimit(block)
            getSuccess()
        }
        confirmVerified(rateLimiter, getSuccess)
    }

    @Test
    fun `rate limit gets applied and reset`() {
        mockkObject(TimeProxy)
        var nowMs = 1560000000L
        every { TimeProxy.currentTimeMillis() } answers { nowMs }
        val delayMs = slot<Long>()
        coEvery { TimeProxy.delay(capture(delayMs)) } answers {
            nowMs += delayMs.captured
        }

        val params = object : RateLimiter.Params {
            override val backoffStart = 1000
            override val backoffFactor = 2f
            override val backoffEnd = 5_000
            override val connectRateLimit = 3
            override val connectRateInterval = 60_000
        }
        val rateLimiter = spyk(RateLimiter(params))

        var success = false
        val getSuccess = spyk<() -> Boolean>(fun() = success)
        val block = suspend { (0 to getSuccess()) }

        runBlocking {
            success = true
            // first attempt happens instantly
            rateLimiter.runWithRateLimit(block)
            TimeProxy.delay(200)
            // successful attempts are not rate limited when below rate,
            // and use backoffStart delay
            rateLimiter.runWithRateLimit(block)
            TimeProxy.delay(200)
            // success does not use exponential backoff, but constant backoffStart
            rateLimiter.runWithRateLimit(block)
            TimeProxy.delay(100)
            // successful attempts are rate limited when above rate
            rateLimiter.runWithRateLimit(block)
            // rate limit cleans up queue
            rateLimiter.runWithRateLimit(block)
            // rate limit expires by waiting
            TimeProxy.delay(1001)
            rateLimiter.runWithRateLimit(block)

            // TODO test: rate limit is not applied to backoff after unsuccessful attempts
        }

        coVerifyOrder {
            // delay(0) is not called
            TimeProxy.delay(200)
            TimeProxy.delay(800) // attempt 2: within rate
            TimeProxy.delay(200)
            TimeProxy.delay(800) // attempt 3: within rate
            TimeProxy.delay(100)
            TimeProxy.delay(params.connectRateInterval - 2100L) // attempt 4: rate limited
            TimeProxy.delay(1000) // attempt 5: rate limited
            TimeProxy.delay(1001)
            // attempt 6: not rate limited
        }
        coVerifyOrder {
            rateLimiter.runWithRateLimit(block)
            getSuccess()
            rateLimiter.runWithRateLimit(block)
            getSuccess()
            rateLimiter.runWithRateLimit(block)
            getSuccess()
            rateLimiter.runWithRateLimit(block)
            getSuccess()
            rateLimiter.runWithRateLimit(block)
            getSuccess()
            rateLimiter.runWithRateLimit(block)
            getSuccess()
        }
        confirmVerified(rateLimiter, getSuccess)
    }
}
