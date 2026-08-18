package com.neoutils.finsight.mcp.surface

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **A period is finished or it is not, and the boundary is the day itself.**
 *
 * `isInProgress` exists so that two totals measured over different windows are not read side by
 * side as a movement — the class KDoc names the failure it was written against, a month measured
 * over eleven days read as a fall in spending. A month on its last day is such a window: it will
 * take postings for the rest of the day.
 *
 * The boundary is fixed here on all three days that can be confused — the first, the last, and the
 * one after — because it is the last day that the arithmetic gets wrong, and nothing else in the
 * suite looks at it.
 */
class AgentPeriodTest {

    private val march = YearMonth(2026, 3)

    @Test
    fun `a month is running on its first day`() {
        assertTrue(AgentPeriod.of(march, today = LocalDate(2026, 3, 1)).isInProgress)
    }

    @Test
    fun `a month is running in the middle of it`() {
        assertTrue(AgentPeriod.of(march, today = LocalDate(2026, 3, 14)).isInProgress)
    }

    @Test
    fun `a month is still running on its last day`() {
        val period = AgentPeriod.of(march, today = LocalDate(2026, 3, 31))

        assertTrue(
            period.isInProgress,
            "the last day of the month was declared finished with a day of postings still to come",
        )
    }

    @Test
    fun `a month is finished the day after it ends`() {
        assertFalse(AgentPeriod.of(march, today = LocalDate(2026, 4, 1)).isInProgress)
    }

    @Test
    fun `a month that has not begun is running`() {
        assertTrue(AgentPeriod.of(march, today = LocalDate(2026, 2, 20)).isInProgress)
    }

    /**
     * The figures of a running month cover today and no further, and on the last day today *is*
     * the end of the period — so the two agree without the boundary being stated twice.
     */
    @Test
    fun `what a period covers stops at today while it runs, and at its end once it has passed`() {
        assertEquals(
            LocalDate(2026, 3, 14),
            AgentPeriod.of(march, today = LocalDate(2026, 3, 14)).measuredThrough,
        )
        assertEquals(
            LocalDate(2026, 3, 31),
            AgentPeriod.of(march, today = LocalDate(2026, 3, 31)).measuredThrough,
        )
        assertEquals(
            LocalDate(2026, 3, 31),
            AgentPeriod.of(march, today = LocalDate(2026, 4, 10)).measuredThrough,
        )
    }

    @Test
    fun `an accumulation reaching today has not finished accumulating`() {
        val today = LocalDate(2026, 3, 31)

        assertTrue(
            AgentPeriod.upTo(to = today, today = today).isInProgress,
            "a balance measured through today was declared complete while the day still runs",
        )
        assertFalse(AgentPeriod.upTo(to = LocalDate(2026, 3, 30), today = today).isInProgress)
    }

    @Test
    fun `an arbitrary range ending today has not finished`() {
        val today = LocalDate(2026, 3, 31)

        assertTrue(
            AgentPeriod.range(from = LocalDate(2026, 1, 1), to = today, today = today).isInProgress,
            "a range ending today was declared complete while the day still runs",
        )
        assertFalse(
            AgentPeriod.range(from = LocalDate(2026, 1, 1), to = LocalDate(2026, 3, 30), today = today).isInProgress,
        )
    }
}
