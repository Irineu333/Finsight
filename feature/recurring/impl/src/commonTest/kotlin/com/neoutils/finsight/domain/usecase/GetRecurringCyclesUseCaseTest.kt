@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.RecurringCycleStatus
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.recurring
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

/**
 * **The four states of a cycle, and the one rule that separates them.**
 *
 * Every cycle of the month is in exactly one of them, and the two that have no occurrence
 * are split by comparing the cycle's *date* with today — never its day of the month with
 * today's. The difference is invisible while the month asked about is the current one and
 * is the whole subject the moment a month can be selected, which is what these tests pin
 * down.
 */
class GetRecurringCyclesUseCaseTest {

    private val useCase = GetRecurringCyclesUseCase(GetUnhandledRecurringUseCase())

    private val month = YearMonth(2026, 8)
    private val today = LocalDate(2026, 8, 10)

    private fun occurrence(
        recurringId: Long,
        status: RecurringOccurrence.Status = RecurringOccurrence.Status.CONFIRMED,
        yearMonth: YearMonth = month,
        day: Int = 5,
    ) = RecurringOccurrence(
        recurringId = recurringId,
        cycleNumber = 1,
        yearMonth = yearMonth,
        status = status,
        effectiveDate = LocalDate(yearMonth.year, yearMonth.month, day),
        handledAt = 0L,
    )

    /** A template born in [YearMonth], so a month before it holds no cycle of its own. */
    private fun bornIn(id: Long, yearMonth: YearMonth, dayOfMonth: Int = 5) = recurring(id = id).copy(
        dayOfMonth = dayOfMonth,
        createdAt = LocalDate(yearMonth.year, yearMonth.month, 1)
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds(),
    )

    @Test
    fun `the four states are mutually exclusive`() {
        val due = recurring(id = 1L).copy(dayOfMonth = 5)
        val ahead = recurring(id = 2L).copy(dayOfMonth = 28)
        val confirmed = recurring(id = 3L).copy(dayOfMonth = 5)
        val skipped = recurring(id = 4L).copy(dayOfMonth = 5)

        val cycles = useCase(
            recurringList = listOf(due, ahead, confirmed, skipped),
            occurrences = listOf(
                occurrence(confirmed.id),
                occurrence(skipped.id, RecurringOccurrence.Status.SKIPPED),
            ),
            month = month,
            today = today,
        )

        assertEquals(listOf(due.id), cycles.pending.map { it.recurring.id })
        assertEquals(listOf(ahead.id), cycles.upcoming.map { it.recurring.id })
        assertEquals(listOf(confirmed.id), cycles.posted.map { it.recurring.id })
        assertEquals(listOf(skipped.id), cycles.skipped.map { it.recurring.id })

        // Said as the partition and not as four assertions that happen to agree: every
        // cycle appears once across the four groups.
        val everyId = RecurringCycleStatus.entries.flatMap { cycles[it] }.map { it.recurring.id }
        assertEquals(everyId.size, everyId.toSet().size)
        assertEquals(4, everyId.size)
    }

    /**
     * A month that has ended has nothing left to be *upcoming*: every date in it is
     * behind today, whatever day the templates fall on.
     */
    @Test
    fun `a past month has nothing upcoming`() {
        val early = recurring(id = 1L).copy(dayOfMonth = 3)
        val late = recurring(id = 2L).copy(dayOfMonth = 28)

        val cycles = useCase(
            recurringList = listOf(early, late),
            occurrences = emptyList(),
            month = YearMonth(2026, 7),
            today = today,
        )

        assertEquals(listOf(early.id, late.id), cycles.pending.map { it.recurring.id })
        assertTrue(cycles.upcoming.isEmpty())
    }

    /**
     * The reading a comparison of days gets wrong. Today is the 10th, so a template due
     * on the 3rd would read as "its day has passed" in *any* month — including a month
     * that has not begun.
     */
    @Test
    fun `a future month has nothing pending`() {
        val early = recurring(id = 1L).copy(dayOfMonth = 3)
        val late = recurring(id = 2L).copy(dayOfMonth = 28)

        val cycles = useCase(
            recurringList = listOf(early, late),
            occurrences = emptyList(),
            month = YearMonth(2026, 9),
            today = today,
        )

        assertTrue(cycles.pending.isEmpty())
        assertEquals(listOf(early.id, late.id), cycles.upcoming.map { it.recurring.id })
    }

    /** Today's own cycle is due, not owed later: the cut is inclusive of today. */
    @Test
    fun `a cycle falling today is pending`() {
        val cycles = useCase(
            recurringList = listOf(recurring(id = 1L).copy(dayOfMonth = today.day)),
            occurrences = emptyList(),
            month = month,
            today = today,
        )

        assertEquals(listOf(1L), cycles.pending.map { it.recurring.id })
    }

    /** A month shorter than the day the template declares resolves onto its last day. */
    @Test
    fun `day 31 in a 30-day month falls on the 30th`() {
        val template = recurring(id = 1L).copy(dayOfMonth = 31)
        val june = YearMonth(2026, 6)

        val cycles = useCase(
            recurringList = listOf(template),
            occurrences = emptyList(),
            month = june,
            today = LocalDate(2026, 6, 30),
        )

        assertEquals(LocalDate(2026, 6, 30), cycles.pending.single().date)
    }

    /** ...and on the 29th of that month it has not come yet. */
    @Test
    fun `day 31 in a 30-day month is upcoming on the 29th`() {
        val template = recurring(id = 1L).copy(dayOfMonth = 31)

        val cycles = useCase(
            recurringList = listOf(template),
            occurrences = emptyList(),
            month = YearMonth(2026, 6),
            today = LocalDate(2026, 6, 29),
        )

        assertEquals(listOf(1L), cycles.upcoming.map { it.recurring.id })
    }

    /** Before the series began there is no cycle at all — not an unhandled one. */
    @Test
    fun `a month before the series began holds no cycle`() {
        val cycles = useCase(
            recurringList = listOf(bornIn(id = 1L, yearMonth = month)),
            occurrences = emptyList(),
            month = YearMonth(2026, 3),
            today = today,
        )

        assertEquals(emptyMap(), cycles.byStatus)
    }

    /** Archiving stops the template generating cycles, in every month, past ones too. */
    @Test
    fun `an archived template is in no section`() {
        val archived = recurring(id = 1L, isArchived = true)

        val cycles = useCase(
            recurringList = listOf(archived),
            // Even with the month's cycle confirmed before the archiving: the money stays
            // in the ledger's own figure, and the row leaves the list.
            occurrences = listOf(occurrence(archived.id)),
            month = month,
            today = today,
        )

        assertEquals(emptyMap(), cycles.byStatus)
    }

    /** An occurrence of another month says nothing about this one. */
    @Test
    fun `an occurrence of another month leaves the cycle unhandled here`() {
        val template = recurring(id = 1L).copy(dayOfMonth = 5)

        val cycles = useCase(
            recurringList = listOf(template),
            occurrences = listOf(occurrence(template.id, yearMonth = YearMonth(2026, 7))),
            month = month,
            today = today,
        )

        assertEquals(listOf(template.id), cycles.pending.map { it.recurring.id })
        assertTrue(cycles.posted.isEmpty())
    }

    /** Within a section the order is the cycle's date, ascending — never creation order. */
    @Test
    fun `a section is ordered by the cycle date`() {
        // Created in the reverse order of their days, so creation order and cycle order
        // disagree and only one of the two can produce this answer. Both are behind
        // today's 10th, so both land in the same section and the order is the subject.
        val eighth = recurring(id = 1L, createdAt = 1L).copy(dayOfMonth = 8)
        val fifth = recurring(id = 2L, createdAt = 2L).copy(dayOfMonth = 5)

        val cycles = useCase(
            recurringList = listOf(eighth, fifth),
            occurrences = emptyList(),
            month = month,
            today = today,
        )

        assertEquals(listOf(fifth.id, eighth.id), cycles.pending.map { it.recurring.id })
    }

    /** The two states with no occurrence are together exactly the unhandled ones. */
    @Test
    fun `unhandled is pending plus upcoming`() {
        val due = recurring(id = 1L).copy(dayOfMonth = 5)
        val ahead = recurring(id = 2L).copy(dayOfMonth = 28)
        val confirmed = recurring(id = 3L).copy(dayOfMonth = 5)

        val cycles = useCase(
            recurringList = listOf(due, ahead, confirmed),
            occurrences = listOf(occurrence(confirmed.id)),
            month = month,
            today = today,
        )

        assertEquals(listOf(due.id, ahead.id), cycles.unhandled.map { it.recurring.id })
        assertTrue(cycles.unhandled.all { it.occurrence == null })
    }

    /** A handled cycle carries the occurrence, which is where its transaction is named. */
    @Test
    fun `a posted cycle carries its occurrence and the date it was filed on`() {
        val confirmed = recurring(id = 1L).copy(dayOfMonth = 5)

        val cycles = useCase(
            recurringList = listOf(confirmed),
            occurrences = listOf(occurrence(confirmed.id, day = 7)),
            month = month,
            today = today,
        )

        val cycle = cycles.posted.single()
        assertEquals(LocalDate(2026, 8, 7), cycle.date)
        assertEquals(confirmed.id, cycle.occurrence?.recurringId)
    }
}
