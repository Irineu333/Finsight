package com.neoutils.finsight.mcp.transport

import com.neoutils.finsight.mcp.contract.ToolErrorCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ToolRateLimiterTest {

    private var elapsed = 0L

    private fun limiter(maxCalls: Int = 3) = ToolRateLimiter(
        maxCalls = maxCalls,
        window = 60.seconds,
        elapsedMillis = { elapsed },
    )

    @Test
    fun `admits up to the limit and refuses the next call`() {
        val limiter = limiter()

        repeat(3) { assertNull(limiter.admit(), "call ${it + 1} is within the limit") }

        assertNotNull(limiter.admit())
    }

    @Test
    fun `the refusal names the limit and is retryable`() {
        val limiter = limiter()
        repeat(3) { limiter.admit() }

        val refusal = assertNotNull(limiter.admit())

        assertEquals(RATE_LIMITED_CODE, refusal.code)
        assertTrue(refusal.isRetryable, "the state of the system says nothing was wrong with the call")
        assertTrue(refusal.message.contains("3"), refusal.message)
        assertTrue(refusal.message.contains("60s"), refusal.message)
    }

    @Test
    fun `it is told apart from a rule of the domain by class, without reading the message`() {
        val limiter = limiter()
        repeat(3) { limiter.admit() }

        val refusal = assertNotNull(limiter.admit())

        assertEquals(ToolErrorCategory.UNAVAILABLE, refusal.category)
        // A rule of the domain is never retryable; this always is. The two are distinguishable
        // on the category alone.
        assertTrue(refusal.category != ToolErrorCategory.DOMAIN_RULE)
        assertTrue(ToolErrorCategory.DOMAIN_RULE.isRetryable != refusal.category.isRetryable)
    }

    @Test
    fun `the refusal is repeatable - the same call is refused the same way`() {
        val limiter = limiter()
        repeat(3) { limiter.admit() }

        val first = assertNotNull(limiter.admit())
        val second = assertNotNull(limiter.admit())

        assertEquals(first, second)
    }

    @Test
    fun `a refused call consumes nothing, so the window still frees exactly what it admitted`() {
        val limiter = limiter()
        repeat(3) { limiter.admit() }
        repeat(10) { assertNotNull(limiter.admit()) }

        elapsed += 60_000

        repeat(3) { assertNull(limiter.admit()) }
    }

    @Test
    fun `the window slides rather than resetting`() {
        val limiter = limiter()

        limiter.admit()
        elapsed += 30_000
        limiter.admit()
        limiter.admit()
        assertNotNull(limiter.admit())

        // Only the first admission has aged out; a fixed window would have freed all three here
        // and admitted twice the limit across the boundary.
        elapsed += 30_000
        assertNull(limiter.admit())
        assertNotNull(limiter.admit())
    }

    @Test
    fun `a limit of zero calls is not expressible`() {
        listOf(0, -1).forEach { maxCalls ->
            val failure = runCatching { ToolRateLimiter(maxCalls = maxCalls) }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException, "maxCalls=$maxCalls must be refused")
        }
    }
}
