package com.neoutils.finsight.domain.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * What the upkeep stops remembering when a currency stops existing.
 *
 * The bound is per pair so that a newly registered currency is never hostage to a
 * synchronisation that already ran that day (design D8b). Codes being reusable, a stamp
 * that outlives its currency reintroduces exactly that: the next currency to wear the
 * code inherits an answer nobody ever gave about it, and waits a day for its first rate.
 */
class RateSyncStateForgettingTest {

    private val noon = Instant.fromEpochMilliseconds(1_772_000_000_000)

    private val state = RateSyncState(
        syncedAt = mapOf(
            RatePair("PEN", "BRL") to noon,
            RatePair("USD", "PEN") to noon,
            RatePair("USD", "BRL") to noon,
        ),
        notCoveredCurrencies = setOf("PEN", "MILHAS"),
    )

    /** On **either** end: the pair is what was asked, and both ends name a currency. */
    @Test
    fun `every pair naming the currency is forgotten`() {
        val forgotten = state.forgetting("PEN")

        assertEquals(setOf(RatePair("USD", "BRL")), forgotten.syncedAt.keys)
    }

    /**
     * *Not covered* is a statement about a currency the app offers. About one it no
     * longer offers it is not false — it is about nothing, and it would come back to life
     * attached to whatever next takes the code.
     */
    @Test
    fun `the refusal to quote it is forgotten too`() {
        assertEquals(setOf("MILHAS"), state.forgetting("PEN").notCoveredCurrencies)
    }

    @Test
    fun `what is remembered about the others is untouched`() {
        val forgotten = state.forgetting("PEN")

        assertEquals(noon, forgotten.syncedAt[RatePair("USD", "BRL")])
        assertTrue("MILHAS" in forgotten.notCoveredCurrencies)
    }

    /** The code is the identity, and the identity is upper case wherever it arrives from. */
    @Test
    fun `the code is normalised`() {
        assertTrue(state.forgetting("pen").syncedAt.keys.none { it.currency == "PEN" || it.against == "PEN" })
    }

    @Test
    fun `forgetting a currency it says nothing about changes nothing`() {
        assertEquals(state, state.forgetting("CHF"))
    }
}
