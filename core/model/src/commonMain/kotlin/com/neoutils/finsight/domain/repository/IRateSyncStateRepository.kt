package com.neoutils.finsight.domain.repository

import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant

/**
 * What the app knows about its own upkeep of the rate archive.
 *
 * @param lastSyncedAt when the archive was last brought up to date **successfully**, or
 * `null` before it ever was. Success is what is persisted, and failure is the absence of
 * a newer instant — there is no error channel here, and that is the decision, not an
 * omission (see [IRateSyncStateRepository]).
 * @param notCoveredCurrencies the currencies the source refused to quote. Not a failure:
 * it is permanent, the user can act on it, and saying it is the only thing that keeps the
 * missing rate from looking like a synchronisation that has not happened yet (design D7).
 */
data class RateSyncState(
    val lastSyncedAt: Instant? = null,
    val notCoveredCurrencies: Set<String> = emptySet(),
)

/**
 * The state of the automatic upkeep of the archive — **and deliberately not an error
 * channel** (design D9).
 *
 * Persisting *when it last worked* is enough for the screen to say what it has to say,
 * and it survives a restart, which an in-memory error state would not. Failed? Nothing is
 * written, and the screen infers it from the old instant. There is no event, no transient
 * state to coordinate, and nothing to clear.
 *
 * **It has exactly one surface**, the rates screen: that screen is not a figure, it is the
 * archive explaining itself, and it is where the *out of date for more than 30 days*
 * signal already lives — without the synchronisation context that signal is an accusation
 * with no defendant. No consolidated figure shows any of this.
 */
interface IRateSyncStateRepository {

    /** The state in force, observable so the rates screen needs no event to notice it. */
    fun observe(): StateFlow<RateSyncState>

    /** Persists [state] — written only when a synchronisation actually succeeded. */
    suspend fun record(state: RateSyncState)
}
