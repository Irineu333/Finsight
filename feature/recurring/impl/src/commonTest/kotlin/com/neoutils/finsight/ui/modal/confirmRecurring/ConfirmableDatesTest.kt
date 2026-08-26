package com.neoutils.finsight.ui.modal.confirmRecurring

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **A confirmation is about a month, and its date may not leave that month.**
 *
 * The occurrence is filed under the month of the date the user picks, while the pending
 * list asks about the month of the cycle. Letting the two diverge filed the confirmation
 * somewhere the pending list never looks: the card stayed on the dashboard, a second
 * confirmation was accepted — the re-entry check is by `(recurringId, yearMonth)` and the
 * months differed — and the same monthly expense entered the ledger twice.
 */
class ConfirmableDatesTest {

    private val august = YearMonth(2026, 8)

    @Test
    fun `the current month is open up to today, and no further`() {
        val window = confirmableDates(cycleMonth = august, today = LocalDate(2026, 8, 14))

        assertEquals(LocalDate(2026, 8, 1), window.start)
        assertEquals(LocalDate(2026, 8, 14), window.endInclusive)
    }

    @Test
    fun `a month already past is open to its whole length`() {
        val window = confirmableDates(cycleMonth = august, today = LocalDate(2026, 11, 3))

        assertEquals(LocalDate(2026, 8, 1), window.start)
        assertEquals(LocalDate(2026, 8, 31), window.endInclusive)
    }

    @Test
    fun `a date from another month is brought back into the cycle's own`() {
        val window = confirmableDates(cycleMonth = august, today = LocalDate(2026, 8, 14))

        assertEquals(LocalDate(2026, 8, 1), LocalDate(2026, 7, 20).coerceIn(window))
        assertEquals(LocalDate(2026, 8, 14), LocalDate(2026, 9, 2).coerceIn(window))
    }

    /**
     * A cycle whose month has not arrived: no surface offers it, and the window still has
     * to be a range rather than an inverted one that `coerceIn` would throw on.
     */
    @Test
    fun `a month still ahead collapses onto its first day`() {
        val window = confirmableDates(cycleMonth = august, today = LocalDate(2026, 7, 30))

        assertEquals(LocalDate(2026, 8, 1), window.start)
        assertEquals(LocalDate(2026, 8, 1), window.endInclusive)
    }
}
