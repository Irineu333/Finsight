@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.mcp.write

import com.neoutils.finsight.mcp.contract.ToolErrorCategory
import com.neoutils.finsight.mcp.contract.ToolOutcome
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class IdempotencyStoreTest {

    private val clock = MovableClock(Instant.parse("2026-03-01T10:00:00Z"))

    private val firstBatch = arguments("""{"items":[{"intent":"EXPENSE","accountId":1}],"key":"a"}""")

    @Test
    fun `a call with no key is never a repeat`() = runTest {
        val store = IdempotencyStore(clock)

        assertEquals(Idempotency.Proceed, store.evaluate(key = null, arguments = firstBatch))
        store.remember(key = null, arguments = firstBatch, outcome = ok("first"))

        assertEquals(Idempotency.Proceed, store.evaluate(key = null, arguments = firstBatch))
        assertEquals(0, store.size())
    }

    @Test
    fun `the same key with the same arguments replays instead of writing again`() = runTest {
        val store = IdempotencyStore(clock)
        val outcome = ok("thirty recorded")

        assertEquals(Idempotency.Proceed, store.evaluate("batch-1", firstBatch))
        store.remember("batch-1", firstBatch, outcome)

        val replay = assertIs<Idempotency.Replay>(store.evaluate("batch-1", firstBatch))
        assertEquals(outcome.structuredContent, replay.outcome.structuredContent)
    }

    @Test
    fun `member order is not part of what a call said`() = runTest {
        val store = IdempotencyStore(clock)
        store.remember("batch-1", arguments("""{"a":1,"b":2}"""), ok("done"))

        assertIs<Idempotency.Replay>(store.evaluate("batch-1", arguments("""{"b":2,"a":1}""")))
    }

    @Test
    fun `the order of the items is part of what a call said`() = runTest {
        val store = IdempotencyStore(clock)
        store.remember("batch-1", arguments("""{"items":[1,2]}"""), ok("done"))

        assertIs<Idempotency.Conflict>(store.evaluate("batch-1", arguments("""{"items":[2,1]}""")))
    }

    @Test
    fun `the same key with other items is a conflict, never a silent no-op`() = runTest {
        val store = IdempotencyStore(clock)
        store.remember("batch-1", firstBatch, ok("first"))

        val conflict = assertIs<Idempotency.Conflict>(
            store.evaluate("batch-1", arguments("""{"items":[{"intent":"EXPENSE","accountId":2}],"key":"a"}""")),
        )

        assertEquals(ToolErrorCategory.CONFLICT, conflict.error.category)
        assertEquals(IdempotencyCodes.KEY_REUSED_WITH_DIFFERENT_ARGUMENTS, conflict.error.code)
        assertEquals(false, conflict.error.isRetryable)
    }

    @Test
    fun `a key stops being honoured once its declared lifetime has passed`() = runTest {
        val store = IdempotencyStore(clock, retention = 1.hours)
        store.remember("batch-1", firstBatch, ok("first"))

        clock.advance(30.minutes)
        assertIs<Idempotency.Replay>(store.evaluate("batch-1", firstBatch))

        clock.advance(31.minutes)
        assertEquals(Idempotency.Proceed, store.evaluate("batch-1", firstBatch))
        assertEquals(0, store.size())
    }

    @Test
    fun `the declared default lifetime is a day`() {
        assertEquals(24.hours, IdempotencyStore.DEFAULT_RETENTION)
    }

    private fun ok(note: String) = ToolOutcome.Ok(arguments("""{"note":"$note"}"""))

    private fun arguments(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject
}

private class MovableClock(private var instant: Instant) : Clock {

    override fun now(): Instant = instant

    fun advance(by: Duration) {
        instant += by
    }
}
