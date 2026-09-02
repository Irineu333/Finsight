@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.backup

import com.neoutils.finsight.domain.vault.VaultState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * When the backup screen is entitled to say the last copy is late.
 *
 * The sign is not the trigger's question re-asked. It carries a sentence — open the app more
 * often and a new copy is taken — and that sentence is true of a vault that goes by the
 * clock and of no other: with the periodic trigger off, opening the app produces nothing,
 * and a copy left standing while the vault captures only before deletions is exactly as
 * recent as the archive let it be. Amber there is a false alarm, and a false alarm spends
 * the only signal design D12 has.
 *
 * Never having captured is the other case that is due without being late, and the screen
 * says that one in words of its own.
 */
class LastCopyOverdueTest {

    private val now = Instant.parse("2026-03-10T12:00:00Z")

    private fun vault(periodic: Boolean, lastCapturedAt: Instant? = null) = VaultState(
        isOn = true,
        isPeriodicOn = periodic,
        interval = 3.days,
        lastCapturedAt = lastCapturedAt,
    )

    @Test
    fun `a copy older than the wait is late while the app goes by the clock`() {
        assertTrue(vault(periodic = true).isCopyOverdue(newest = now - 5.days, now = now))
    }

    @Test
    fun `a copy younger than the wait is not late`() {
        assertFalse(vault(periodic = true).isCopyOverdue(newest = now - 1.days, now = now))
    }

    @Test
    fun `nothing is late with the periodic trigger off`() {
        assertFalse(
            vault(periodic = false).isCopyOverdue(newest = now - 300.days, now = now),
            "no trigger goes by the clock, so opening the app would produce nothing",
        )
    }

    @Test
    fun `a vault that has never captured is due but not late`() {
        val vault = vault(periodic = true, lastCapturedAt = null)

        assertTrue(vault.isIntervalDue(now), "the trigger still has to take the first copy")
        assertFalse(vault.isCopyOverdue(newest = null, now = now), "there is no copy to call old")
    }

    /**
     * The amber sits over the instant the card shows, which is the newest copy standing where
     * the copies go — not this install's own last capture. A folder somebody has just pointed
     * at holds copies that were taken by an install this one knows nothing about, and warning
     * that a copy from this morning is older than the wait is a false alarm spending the only
     * signal design D12 has.
     */
    @Test
    fun `the wait is measured against the copy the card names, not this install's capture`() {
        val vault = vault(periodic = true, lastCapturedAt = now - 40.days)

        assertFalse(
            vault.isCopyOverdue(newest = now - 1.days, now = now),
            "the amber was drawn from an instant the card does not show",
        )
        assertTrue(vault.isIntervalDue(now), "and the trigger's own question is untouched")
    }
}
