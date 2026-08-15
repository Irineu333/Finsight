package com.neoutils.finsight.mcp.server

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive

/**
 * How a tool call ended with respect to **interruption** — not with respect to the domain, which
 * is what `ToolOutcome` answers.
 */
sealed interface CallCompletion<out T> {

    /** The body ran to the end. Its value is the answer to send. */
    data class Produced<T>(val value: T) : CallCompletion<T>

    /**
     * The client cancelled the request by explicit notification, and the body stopped.
     *
     * **Nothing further is emitted for that request** — no result, no error, no progress. The
     * protocol's own rule, and the reason this is a completion of its own rather than a failure:
     * a failure would be answered, and answering a cancelled request is precisely what is
     * forbidden.
     */
    data object Silenced : CallCompletion<Nothing>
}

/**
 * Progress on a long-running call — a large batch, an aggregate over a wide period.
 *
 * Emission is best effort and **stops the moment the call is cancelled**: the sink handed to the
 * body by [underCancellation] drops every report made after that point rather than putting one
 * more message on the wire for a request that is no longer owed anything.
 */
fun interface ProgressSink {

    /**
     * @param progress how much has been done, in the same unit as [total].
     * @param total how much there is to do, or `null` when it is not known in advance.
     * @param message a short English note for a log, or `null`.
     */
    suspend fun report(progress: Double, total: Double?, message: String?)

    companion object {
        /** Emits nothing. What a call with no progress to report is given. */
        val Silent: ProgressSink = ProgressSink { _, _, _ -> }
    }
}

/**
 * Runs [block] as the body of a tool call, under the cancellation rules of revision
 * `2025-11-25` — the revision this server targets, and the one whose cancellation rule is
 * *inverted* with respect to the revision after it.
 *
 * **Losing the connection is not cancellation.** In this revision the client cancels by sending
 * `notifications/cancelled`, and nothing else cancels: an HTTP exchange that dies mid-flight says
 * only that nobody is listening any more, never that the work should stop. That is why the body
 * runs on the protocol's handler scope rather than on the scope of the HTTP call — a scope this
 * server takes care never to tie to an exchange — and why a batch whose caller vanished goes on
 * to completion. In the next revision closing the stream *is* cancelling; when the migration
 * happens, this is the assumption to revisit first.
 *
 * **What was applied stays applied, in either case.** Neither interruption rolls anything back:
 * an interrupted batch leaves the ledger in a state that has a name, and repeating the call with
 * the same idempotency key finishes what was missing instead of duplicating what got in. This
 * function is deliberately not a transaction boundary — inventing one here would make a partial
 * batch unrecoverable, which is the outcome the idempotency key exists to prevent.
 *
 * @param progress where the body reports progress. Wrapped so that reports made after
 * cancellation are dropped.
 * @return [CallCompletion.Produced] with the body's value, or [CallCompletion.Silenced] when the
 * client cancelled.
 */
suspend fun <T> underCancellation(
    progress: ProgressSink = ProgressSink.Silent,
    block: suspend (ProgressSink) -> T,
): CallCompletion<T> {
    val silencing = ProgressSink { done, total, message ->
        if (currentCoroutineContext().isActive) progress.report(done, total, message)
    }

    return try {
        CallCompletion.Produced(block(silencing))
    } catch (_: CancellationException) {
        CallCompletion.Silenced
    }
}

/**
 * Stops the call here if the client has cancelled it — the cooperative half of "stops as soon as
 * practicable".
 *
 * A batch calls this between items. Cancellation cannot interrupt a running item, and should not:
 * an item half applied is exactly the state no one can name. Between items the ledger is
 * consistent, so that is where stopping is free.
 */
suspend fun stopIfCancelled() {
    currentCoroutineContext().ensureActive()
}
