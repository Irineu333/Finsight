package com.neoutils.finsight.database

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

/**
 * The declared retention of the agent activity log, in one place.
 *
 * **Two ceilings, and a row has to satisfy both.** Neither is enough on its own: an age limit
 * alone bounds nothing, since a runaway agent can write millions of rows in an afternoon and
 * every one of them is recent; a count limit alone lets a single burst evict the month-old
 * trace the user came looking for. So [MAX_AGE] bounds how long a trace is kept, which is the
 * question the user asks, and [MAX_ENTRIES] bounds how large the table can get, which is the
 * guarantee the app owes the device.
 *
 * The two are sized so that age is the ceiling that normally bites and count is the safety
 * net: at any plausible rate of legitimate use, a row falls out because it got old, not
 * because it got crowded out.
 *
 * Discarding a row is not undoing anything. What the row described lives in the ledger, and
 * that is the only reason a log may be trimmed at all.
 */
object AgentActivityRetention {

    /**
     * The most rows the log ever holds. Beyond it the oldest are discarded.
     *
     * Five thousand is roughly a hundred days of heavy agent use, so it is well past what
     * [MAX_AGE] would have removed first — which is the intent: this ceiling exists for the
     * agent that loops, not for the one that works.
     */
    const val MAX_ENTRIES: Int = 5_000

    /**
     * How long a trace is kept.
     *
     * Six months. The investigation the log exists for happens in days — the user notices the
     * odd figure the next morning and goes looking for where it came from — and half a year is
     * generous for that while keeping the log a trace rather than an archive.
     */
    val MAX_AGE: Duration = 180.days
}
