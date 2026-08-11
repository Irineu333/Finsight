package com.neoutils.finsight.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where each shortcut opens. The two projections are the only owners of the rule, and the
 * cap at today is what keeps the gesture meaning what it says in a month still running.
 */
class BalanceAdjustmentDateTest {

    private val today = LocalDate(2026, 8, 11)

    @Test
    fun `closing a past month lands on the last day of that month`() {
        assertEquals(
            LocalDate(2026, 3, 31),
            closingBalanceDateOf(month = YearMonth(2026, 3), today = today),
        )
    }

    @Test
    fun `closing the current month lands on today and not on a day that has not happened`() {
        assertEquals(
            today,
            closingBalanceDateOf(month = YearMonth(2026, 8), today = today),
        )
    }

    @Test
    fun `opening a month lands on the last day of the month before it`() {
        assertEquals(
            LocalDate(2026, 2, 28),
            openingBalanceDateOf(month = YearMonth(2026, 3), today = today),
        )
    }

    @Test
    fun `a future month is capped at today on both sides`() {
        assertEquals(today, closingBalanceDateOf(month = YearMonth(2026, 11), today = today))
        assertEquals(today, openingBalanceDateOf(month = YearMonth(2026, 11), today = today))
    }

    /**
     * The last day is asked of the month, never assembled from a day number, so a short
     * month cannot overflow.
     */
    @Test
    fun `short months keep their own last day`() {
        val later = LocalDate(2030, 12, 31)

        assertEquals(
            LocalDate(2026, 2, 28),
            closingBalanceDateOf(month = YearMonth(2026, 2), today = later),
        )
        assertEquals(
            LocalDate(2028, 2, 29),
            closingBalanceDateOf(month = YearMonth(2028, 2), today = later),
        )
        assertEquals(
            LocalDate(2028, 2, 29),
            openingBalanceDateOf(month = YearMonth(2028, 3), today = later),
        )
    }
}
