@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime

/**
 * **Where a series begins, and how its months are numbered.**
 *
 * `createdAt` is the anchor the numbering is counted from, and until it was read here it
 * was read in four places by hand — twice as a cycle number, twice as a predicate about a
 * month. The two readings have to agree by construction: the month a template is asked to
 * confirm and the number that confirmation records are the same fact seen twice.
 */
class RecurringCycleTest {

    private fun bornIn(month: YearMonth) = Recurring(
        id = 1,
        type = TransactionType.EXPENSE,
        amount = 100.0,
        title = "Aluguel",
        dayOfMonth = 5,
        category = null,
        account = null,
        creditCard = null,
        createdAt = LocalDate(month.year, month.month, 1)
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds(),
    )

    private val august = YearMonth(2026, 8)

    @Test
    fun `the origin month is cycle one`() {
        assertEquals(august, bornIn(august).originMonth)
        assertEquals(1, bornIn(august).cycleNumberIn(august))
    }

    @Test
    fun `the months after it are numbered from there`() {
        val template = bornIn(august)

        assertEquals(2, template.cycleNumberIn(YearMonth(2026, 9)))
        assertEquals(6, template.cycleNumberIn(YearMonth(2027, 1)))
    }

    @Test
    fun `a month before the origin has no cycle at all`() {
        val template = bornIn(august)

        // Not zero, and not a negative ordinal: there is nothing there to number.
        assertNull(template.cycleNumberIn(YearMonth(2026, 7)))
        assertNull(template.cycleNumberIn(YearMonth(2025, 12)))
    }

    @Test
    fun `generating a cycle needs the month to have come and the template to be active`() {
        val template = bornIn(august)

        assertEquals(false, template.generatesCycleIn(YearMonth(2026, 7)))
        assertEquals(true, template.generatesCycleIn(august))
        assertEquals(true, template.generatesCycleIn(YearMonth(2026, 9)))
        assertEquals(false, template.copy(isArchived = true).generatesCycleIn(august))
    }
}
