package com.neoutils.finsight

import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.Event
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList

/**
 * The analytics double of this module's tests: it records instead of sending, so a test
 * that cares about an event asserts on it, and one that does not simply ignores what it
 * collected.
 */
class RecordingAnalytics : Analytics {

    private val recorded = MutableSharedFlow<Event>(replay = RECORDED_LIMIT)

    /** What has been logged **so far** — read it only when the write is known to be done. */
    val events: List<Event> get() = recorded.replayCache

    /**
     * Suspends until [count] events have been logged. A write that lands on another
     * dispatcher reports itself there too, so reading [events] straight after asking for
     * the write is racing it.
     */
    suspend fun awaitEvents(count: Int = 1): List<Event> = recorded.take(count).toList()

    override fun logScreenView(screenName: String) = Unit
    override fun logEvent(event: Event) { recorded.tryEmit(event) }
    override fun setUserId(id: String?) = Unit

    private companion object {
        /** Far above what one action reports; nothing under test logs in bulk. */
        const val RECORDED_LIMIT = 64
    }
}
