package com.neoutils.finsight.mcp.server

import com.neoutils.finsight.mcp.FakeMcpServerSettingsRepository
import com.neoutils.finsight.mcp.McpServerController
import com.neoutils.finsight.mcp.McpTestClient
import com.neoutils.finsight.mcp.TestTool
import com.neoutils.finsight.mcp.contract.ToolOutcome
import com.neoutils.finsight.mcp.contract.ToolRegistry
import com.neoutils.finsight.mcp.enabledSettings
import com.neoutils.finsight.mcp.freePort
import com.neoutils.finsight.mcp.noPrompts
import com.neoutils.finsight.mcp.noResources
import com.neoutils.finsight.mcp.notification
import com.neoutils.finsight.mcp.request
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.net.http.HttpTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CancellationHandlingTest {

    // ── The rule, in isolation ───────────────────────────────────────────────────────────

    @Test
    fun `a body that runs to the end produces its value`() = runTest {
        val completion = underCancellation { "done" }

        assertEquals(CallCompletion.Produced("done"), completion)
    }

    @Test
    fun `progress reaches the sink while the call is live`() = runTest {
        val reported = mutableListOf<String>()

        underCancellation(progress = { _, _, message -> reported += message.orEmpty() }) { sink ->
            sink.report(1.0, 2.0, "half")
            sink.report(2.0, 2.0, "all")
        }

        assertEquals(listOf("half", "all"), reported)
    }

    @Test
    fun `a cancelled call is silenced, and emits nothing further`() = runTest {
        val reported = mutableListOf<String>()
        var completion: CallCompletion<Unit>? = null

        val job = launch {
            completion = underCancellation(progress = { _, _, m -> reported += m.orEmpty() }) { sink ->
                sink.report(1.0, 3.0, "before")
                currentCoroutineContext().cancel()
                sink.report(2.0, 3.0, "after")
                stopIfCancelled()
            }
        }
        job.join()

        assertEquals(CallCompletion.Silenced, completion)
        assertEquals(listOf("before"), reported, "nothing is emitted for a request already cancelled")
    }

    @Test
    fun `a body parked forever is silenced when the peer cancels it`() = runTest {
        val entered = CompletableDeferred<Unit>()
        var completion: CallCompletion<Unit>? = null

        val job = launch {
            completion = underCancellation {
                entered.complete(Unit)
                awaitCancellation()
            }
        }
        entered.await()
        job.cancelAndJoin()

        assertEquals(CallCompletion.Silenced, completion)
    }

    @Test
    fun `stopping between items is where a batch stops`() = runTest {
        val job = launch {
            currentCoroutineContext().cancel()
            assertFailsWith<kotlinx.coroutines.CancellationException> { stopIfCancelled() }
        }
        job.join()
    }

    // ── The rule, over a real connection ─────────────────────────────────────────────────

    @Test
    fun `an explicit cancellation stops the call and nothing further is emitted for it`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val observedCancellation = AtomicBoolean(false)
        val tool = TestTool(name = "finsight_slow") {
            entered.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                observedCancellation.set(true)
            }
        }

        withServer(tool) { client ->
            val call = launch(Dispatchers.IO) {
                client.post(
                    request(
                        id = CALL_ID,
                        method = "tools/call",
                        params = """{"name":"finsight_slow","arguments":{}}""",
                    ),
                )
            }
            entered.await()

            val acknowledgement = withContext(Dispatchers.IO) {
                client.post(notification("notifications/cancelled", """{"requestId":$CALL_ID}"""))
            }
            assertEquals(202, acknowledgement.statusCode())

            call.join()
            // Cancellation propagates into the handler asynchronously: the exchange is retired as
            // soon as the request is, which is before the body has necessarily unwound.
            assertTrue(awaitTrue(observedCancellation), "the tool was not stopped")
        }
    }

    @Test
    fun `losing the connection is not a cancellation - the work runs on`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val finished = AtomicBoolean(false)
        val cancelled = AtomicBoolean(false)
        val tool = TestTool(name = "finsight_slow") {
            entered.complete(Unit)
            try {
                delay(WORK_MILLIS)
                finished.set(true)
            } catch (cause: kotlinx.coroutines.CancellationException) {
                cancelled.set(true)
                throw cause
            }
            ToolOutcome.Ok(JsonObject(emptyMap()))
        }

        withServer(tool) { client ->
            // The client walks away mid-call. No cancellation notification is ever sent.
            withContext(Dispatchers.IO) {
                assertFailsWith<HttpTimeoutException> {
                    client.post(
                        body = request(
                            id = CALL_ID,
                            method = "tools/call",
                            params = """{"name":"finsight_slow","arguments":{}}""",
                        ),
                        timeoutMillis = ABORT_MILLIS,
                    )
                }
            }
            entered.await()

            assertTrue(awaitTrue(finished), "the work must have run to the end")
            assertFalse(cancelled.get(), "a dropped connection must not be read as a cancellation")
        }
    }

    /** Waits for [flag], because the events being asserted about are raised off this thread. */
    private suspend fun awaitTrue(flag: AtomicBoolean): Boolean {
        repeat(POLLS) {
            if (flag.get()) return true
            delay(POLL_MILLIS)
        }
        return false
    }

    private suspend fun withServer(tool: TestTool, block: suspend (McpTestClient) -> Unit) {
        val port = freePort()
        val token = "cancellation-token"
        val controller = McpServerController(
            settings = FakeMcpServerSettingsRepository(enabledSettings(port, token)),
            tools = ToolRegistry(listOf(tool)),
            resources = noResources(),
            prompts = noPrompts(),
        )
        controller.start()
        try {
            val client = McpTestClient(port, token)
            withContext(Dispatchers.IO) { client.initialize() }
            block(client)
        } finally {
            controller.stop()
        }
    }

    private companion object {
        const val CALL_ID = 42
        const val WORK_MILLIS = 700L
        const val ABORT_MILLIS = 200L
        const val POLLS = 100
        const val POLL_MILLIS = 50L
    }
}
