package com.neoutils.finsight.mcp.transport

import com.neoutils.finsight.mcp.contract.ToolError
import com.neoutils.finsight.mcp.contract.ToolErrorCategory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * The stable code a rate-limited call is refused under. Enumerated in every tool's output
 * schema, so a consumer can branch on it without having seen one first.
 */
const val RATE_LIMITED_CODE: String = "RATE_LIMITED"

/**
 * How many tool invocations this server admits inside [ToolRateLimiter.window] — the refusal the
 * revision requires as a `MUST`, and the one thing standing between a prompt loop and a write
 * loop over the ledger.
 *
 * An agent writes in batches and repeats on its own judgement. The limit is a **named, repeatable
 * refusal**: [ToolErrorCategory.UNAVAILABLE], so it is retryable by class, which is precisely how
 * it is told apart from a refusal by a rule of the domain — that one is
 * [ToolErrorCategory.DOMAIN_RULE] and is never retryable, because the state of the system is
 * correct and trying again yields the same answer. Reading the message is never necessary to tell
 * the two apart.
 *
 * The limiter is consulted **before** the tool is reached, so a refused call performs no write —
 * that is the whole point of putting it here rather than inside a tool.
 *
 * It is a sliding window rather than a fixed one: a fixed window admits twice the limit across
 * the instant it resets, which a batching agent finds immediately.
 *
 * @param maxCalls how many invocations are admitted per [window].
 * @param window the span the count slides over.
 * @param elapsedMillis a monotonic clock, in milliseconds. Injected so the behaviour at the edge
 * of the window is tested by moving time rather than by waiting for it.
 */
class ToolRateLimiter(
    val maxCalls: Int = DEFAULT_MAX_CALLS,
    val window: Duration = DEFAULT_WINDOW,
    private val elapsedMillis: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
) {

    init {
        require(maxCalls > 0) { "A rate limit admits at least one call per window: $maxCalls" }
        require(window.isPositive()) { "A rate limit window is positive: $window" }
    }

    private val windowMillis = window.inWholeMilliseconds

    /** Admission instants inside the window, oldest first. Never larger than [maxCalls]. */
    private val admitted = ArrayDeque<Long>(maxCalls)

    private val lock = Any()

    /**
     * Admits one invocation, or refuses it.
     *
     * @return `null` when the call may proceed, or the refusal to answer it with. The refusal is
     * a [ToolError] rather than an exception because the whole surface answers refusals inside a
     * result, and a rate limit is not a protocol error.
     */
    fun admit(): ToolError? = synchronized(lock) {
        val now = elapsedMillis()
        while (admitted.isNotEmpty() && now - admitted.first() >= windowMillis) {
            admitted.removeFirst()
        }

        if (admitted.size >= maxCalls) {
            return@synchronized ToolError.unavailable(
                code = RATE_LIMITED_CODE,
                message = "Rate limit reached: at most $maxCalls tool calls per ${window.inWholeSeconds}s. " +
                    "Nothing was written. Retry after the window slides.",
            )
        }

        admitted.addLast(now)
        null
    }

    companion object {

        /**
         * Generous for a human working through an agent, and far below what an unattended loop
         * reaches in a second. The limit exists to bound a runaway, not to pace a user.
         */
        const val DEFAULT_MAX_CALLS: Int = 120

        val DEFAULT_WINDOW: Duration = 1.minutes

        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
