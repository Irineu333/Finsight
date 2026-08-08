package com.neoutils.finsight.database.repository

import com.neoutils.finsight.domain.repository.RatePair
import com.neoutils.finsight.domain.repository.RateSyncState
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

/** Success is what is persisted, and *never synchronised* is a state, not a date. */
class RateSyncStateRepositoryTest {

    private val settings = MapSettings()

    @Test
    fun `a repository that has never synchronised says so`() {
        val state = RateSyncStateRepository(settings).observe().value

        assertNull(state.lastSyncedAt)
        assertEquals(emptySet(), state.notCoveredCurrencies)
    }

    @Test
    fun `what was recorded is what a reopened repository reads`() = runTest {
        val recorded = RateSyncState(
            syncedAt = mapOf(
                RatePair("USD", "BRL") to Instant.fromEpochMilliseconds(1_784_000_000_000),
                RatePair("EUR", "BRL") to Instant.fromEpochMilliseconds(1_784_000_060_000),
            ),
            notCoveredCurrencies = setOf("ARS", "VES"),
        )

        RateSyncStateRepository(settings).record(recorded)

        assertEquals(recorded, RateSyncStateRepository(settings).observe().value)
    }

    @Test
    fun `recording emits on the flow the screen observes`() = runTest {
        val repository = RateSyncStateRepository(settings)
        val recorded = RateSyncState(
            syncedAt = mapOf(RatePair("USD", "BRL") to Instant.fromEpochMilliseconds(1_784_000_000_000)),
            notCoveredCurrencies = setOf("ARS"),
        )

        repository.record(recorded)

        assertEquals(recorded, repository.observe().value)
    }

    /**
     * What the screen states is the most recent of the per-currency instants, derived and
     * never a second stored field — two copies would part company on the first partial
     * round, one currency answering while another failed.
     */
    @Test
    fun `the instant the screen states is the most recent one`() = runTest {
        val newest = Instant.fromEpochMilliseconds(1_784_000_060_000)

        RateSyncStateRepository(settings).record(
            RateSyncState(
                syncedAt = mapOf(
                    RatePair("USD", "BRL") to Instant.fromEpochMilliseconds(1_784_000_000_000),
                    RatePair("EUR", "BRL") to newest,
                    RatePair("GBP", "BRL") to Instant.fromEpochMilliseconds(1_783_000_000_000),
                ),
            )
        )

        assertEquals(newest, RateSyncStateRepository(settings).observe().value.lastSyncedAt)
    }
}
