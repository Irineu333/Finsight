@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.recurring
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

/**
 * The predicate answers about the **month**, never about the day: a template still due
 * later in the month is as unhandled as one whose day has passed. What takes a template
 * out is an occurrence in that month — confirmed or skipped alike — or being archived.
 */
class GetUnhandledRecurringUseCaseTest {

    private val useCase = GetUnhandledRecurringUseCase()

    private val july = YearMonth(2026, 7)

    // The fixture is due on day 5; nothing below reads a day, which is the point.
    private val active = recurring(id = 1L)
    private val archived = recurring(id = 2L, isArchived = true)

    private fun occurrence(
        recurringId: Long,
        month: YearMonth,
        status: RecurringOccurrence.Status = RecurringOccurrence.Status.CONFIRMED,
    ) = RecurringOccurrence(
        id = recurringId,
        recurringId = recurringId,
        cycleNumber = 1,
        yearMonth = month,
        status = status,
        effectiveDate = LocalDate(month.year, month.month, 5),
        handledAt = 0,
    )

    @Test
    fun `a template of the month whose day has not come is unhandled`() {
        val unhandled = useCase(
            recurringList = listOf(active),
            occurrences = emptyList(),
            month = july,
        )

        assertEquals(listOf(active.id), unhandled.map { it.id })
    }

    @Test
    fun `an occurrence in the month takes the template out`() {
        val unhandled = useCase(
            recurringList = listOf(active),
            occurrences = listOf(occurrence(active.id, july)),
            month = july,
        )

        assertEquals(emptyList(), unhandled.map { it.id })
    }

    @Test
    fun `a skipped cycle is handled just as a confirmed one is`() {
        val unhandled = useCase(
            recurringList = listOf(active),
            occurrences = listOf(occurrence(active.id, july, RecurringOccurrence.Status.SKIPPED)),
            month = july,
        )

        assertEquals(emptyList(), unhandled.map { it.id })
    }

    @Test
    fun `an occurrence in another month says nothing about this one`() {
        val unhandled = useCase(
            recurringList = listOf(active),
            occurrences = listOf(occurrence(active.id, YearMonth(2026, 6))),
            month = july,
        )

        assertEquals(listOf(active.id), unhandled.map { it.id })
    }

    /** The anchor `createdAt` names, read the same way the cycle numbering reads it. */
    private fun bornIn(month: YearMonth) = recurring(
        id = 3L,
        createdAt = LocalDate(month.year, month.month, 1)
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds(),
    )

    @Test
    fun `a template is not unhandled in a month before its own origin`() {
        // The series begins in the month its anchor falls in — that is the month
        // `ConfirmRecurringUseCase` numbers 1. There is no cycle 0 to be unhandled for.
        val unhandled = useCase(
            recurringList = listOf(bornIn(july)),
            occurrences = emptyList(),
            month = YearMonth(2026, 6),
        )

        assertEquals(emptyList(), unhandled.map { it.id })
    }

    @Test
    fun `the origin month itself is the first the template is unhandled for`() {
        val unhandled = useCase(
            recurringList = listOf(bornIn(july)),
            occurrences = emptyList(),
            month = july,
        )

        assertEquals(listOf(3L), unhandled.map { it.id })
    }

    @Test
    fun `an archived template is never unhandled`() {
        val unhandled = useCase(
            recurringList = listOf(active, archived),
            occurrences = emptyList(),
            month = july,
        )

        assertEquals(listOf(active.id), unhandled.map { it.id })
    }
}
