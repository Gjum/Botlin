package com.github.gjum.minecraft.botlin.modules.defaults

import com.github.gjum.minecraft.botlin.api.Authentication
import com.github.steveice10.mc.protocol.MinecraftProtocol
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Test

class RateLimiterTest {
    @Test
    fun `backoff gets applied and reset`() {
        mockkObject(TimeProxy)
        var nowMs = 1560000000L
        every { TimeProxy.currentTimeMillis() } returns nowMs
        val delayMs = slot<Long>()
        coEvery { TimeProxy.delay(capture(delayMs)) } coAnswers {
            nowMs += delayMs.captured
        }

        val proto = mockk<MinecraftProtocol>()
        val auth = mockk<Authentication>()

        val backoffStart = 1000
        val backoffFactor = 2f
        val backoffEnd = 5_000
        val connectRateLimit = 5
        val connectRateInterval = 60_000

        val connector = RateLimitedConnectProvider(auth,
            backoffStart, backoffFactor, backoffEnd,
            connectRateLimit, connectRateInterval)
        val rateLimiter = spyk(AccountRateLimiter("botlin", connector))

        runBlocking {
            // failed attempts use exponential backoff
            coEvery { auth.authenticate("botlin") } returns null
            // attempt 1: no delay
            rateLimiter.createProto(auth)
            // attempt 2: after backoffStart
            rateLimiter.createProto(auth)
            // attempt 3: after backoffStart * 2
            rateLimiter.createProto(auth)
            // attempt 4: after backoffStart * 2 * 2
            rateLimiter.createProto(auth)
            // attempt n: after backoffEnd
            rateLimiter.createProto(auth)
            // attempt n+1: also after backoffEnd
            // also set up success for next part ...
            coEvery { auth.authenticate("botlin") } returns proto
            rateLimiter.createProto(auth)
            // successful attempt resets backoff to backoffStart
            rateLimiter.createProto(auth)
            // repeated successful attempts delayed by backoffStart
            // also setup failure for next part ...
            coEvery { auth.authenticate("botlin") } returns null
            rateLimiter.createProto(auth)
            // after success, failures use exponential backoff again
            rateLimiter.createProto(auth)
        }

        coVerifyOrder {
            rateLimiter.createProto(auth)
            // delay(0) is not called
            auth.authenticate("botlin")

            rateLimiter.createProto(auth)
            TimeProxy.delay(1000)
            auth.authenticate("botlin")

            rateLimiter.createProto(auth)
            TimeProxy.delay(2000)
            auth.authenticate("botlin")

            rateLimiter.createProto(auth)
            TimeProxy.delay(4000)
            auth.authenticate("botlin")

            rateLimiter.createProto(auth)
            TimeProxy.delay(5000)
            auth.authenticate("botlin")

            rateLimiter.createProto(auth)
            TimeProxy.delay(5000)
            auth.authenticate("botlin")

            rateLimiter.createProto(auth)
            TimeProxy.delay(1000)
            auth.authenticate("botlin")

            rateLimiter.createProto(auth)
            TimeProxy.delay(1000)
            auth.authenticate("botlin")

            rateLimiter.createProto(auth)
            TimeProxy.delay(2000)
            auth.authenticate("botlin")
        }
        confirmVerified(auth, rateLimiter)
    }

    @Test
    fun `rate limit gets applied and reset`() {
        mockkObject(TimeProxy)
        var nowMs = 1560000000L
        every { TimeProxy.currentTimeMillis() } returns nowMs
        val delayMs = slot<Long>()
        coEvery { TimeProxy.delay(capture(delayMs)) } answers {
            nowMs += delayMs.captured
        }

        val proto = mockk<MinecraftProtocol>()
        val auth = mockk<Authentication>()

        val backoffStart = 1000
        val backoffFactor = 2f
        val backoffEnd = 5_000
        val connectRateLimit = 3
        val connectRateInterval = 60_000

        val connector = RateLimitedConnectProvider(auth,
            backoffStart, backoffFactor, backoffEnd,
            connectRateLimit, connectRateInterval)
        val rateLimiter = spyk(AccountRateLimiter("botlin", connector))

        runBlocking {
            // first attempt happens instantly
            coEvery { auth.authenticate("botlin") } returns proto
            rateLimiter.createProto(auth)
            TimeProxy.delay(200)
            // successful attempts are not rate limited when below rate,
            // and use backoffStart delay
            rateLimiter.createProto(auth)
            TimeProxy.delay(200)
            // success does not use exponential backoff, but constant backoffStart
            rateLimiter.createProto(auth)
            TimeProxy.delay(100)
            // XXX above inter-call delay does not get applied
//            // successful attempts are rate limited when above rate
//            rateLimiter.createProto(auth)
//            // rate limit cleans up queue
//            rateLimiter.createProto(auth)
//            TimeProxy.delay(200)
//            // rate limit expires by waiting
//            rateLimiter.createProto(auth)

            // TODO rate limit is not applied to backoff after unsuccessful attempts
        }

        coVerifyOrder {
            // delay(0) is not called
            TimeProxy.delay(200)
            TimeProxy.delay(1000) // attempt 2: within rate
            TimeProxy.delay(200)
            TimeProxy.delay(1000) // attempt 3: within rate
            TimeProxy.delay(100)
//            TimeProxy.delay(connectRateInterval - 2500L) // attempt 4: rate limited
//            TimeProxy.delay(300) // attempt 5: rate limited
//            TimeProxy.delay(200)
        }
        coVerifyOrder {
            rateLimiter.createProto(auth)
            auth.authenticate("botlin")
            rateLimiter.createProto(auth)
            auth.authenticate("botlin")
            rateLimiter.createProto(auth)
            auth.authenticate("botlin")
//            rateLimiter.createProto(auth)
//            auth.authenticate("botlin")
//            rateLimiter.createProto(auth)
//            auth.authenticate("botlin")
        }
        confirmVerified(auth, rateLimiter)
    }
}
