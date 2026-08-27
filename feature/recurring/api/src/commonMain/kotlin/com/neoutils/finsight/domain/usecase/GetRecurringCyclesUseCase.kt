package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringCycle
import com.neoutils.finsight.domain.model.RecurringCycleStatus
import com.neoutils.finsight.domain.model.RecurringCycles
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.extension.safeOnDay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * The owner of "what state is each cycle of this month in".
 *
 * There is one partition and one place it is computed. Every surface that needs any of
 * the four states asks here — the screen for its sections, the dashboard for its pending
 * card, the month overview for its forecast — because four predicates spread over four
 * consumers is four chances to disagree about the same month.
 *
 * **The two halves are asked for, never recomputed.** What has nothing recorded is
 * [GetUnhandledRecurringUseCase]'s answer, and what has is the month's occurrences read
 * by their status. Neither half gets a predicate of its own, so the sets stay
 * complementary by construction.
 *
 * **The cut between pending and upcoming compares dates, not days of the month.**
 * `effectiveDay(day) <= today.day` is only correct while the month being looked at is
 * the current one; with a month the user selects it resolves the day over one month and
 * compares it with the day of another — a template due on the 3rd would read as pending
 * in every future month whose 3rd has "passed" in the current one. Resolving the day
 * onto [month] and comparing whole dates is one predicate for every month, and the
 * degenerate cases fall out of it: a past month has everything pending, a future one
 * everything upcoming.
 *
 * @param today the date the cut is made against, supplied by the caller so that the rule
 * has no clock of its own to disagree with the caller's.
 */
class GetRecurringCyclesUseCase(
    private val getUnhandledRecurring: GetUnhandledRecurringUseCase,
) {
    operator fun invoke(
        recurringList: List<Recurring>,
        occurrences: List<RecurringOccurrence>,
        month: YearMonth,
        today: LocalDate,
    ): RecurringCycles {
        // The templates that have a cycle in this month at all — the series has begun and
        // the template is not archived. Everything below is drawn from this set, so an
        // archived template is absent from all four states rather than filtered out of
        // each of them.
        val ofTheMonth = recurringList
            .filter { it.generatesCycleIn(month) }
            .associateBy { it.id }

        val projected = getUnhandledRecurring(
            recurringList = recurringList,
            occurrences = occurrences,
            month = month,
        ).map { recurring ->
            val date = month.safeOnDay(recurring.dayOfMonth)
            RecurringCycle(
                recurring = recurring,
                status = if (date <= today) {
                    RecurringCycleStatus.PENDING
                } else {
                    RecurringCycleStatus.UPCOMING
                },
                date = date,
            )
        }

        val handled = occurrences.mapNotNull { occurrence ->
            if (occurrence.yearMonth != month) return@mapNotNull null
            val recurring = ofTheMonth[occurrence.recurringId] ?: return@mapNotNull null

            RecurringCycle(
                recurring = recurring,
                status = when (occurrence.status) {
                    RecurringOccurrence.Status.CONFIRMED -> RecurringCycleStatus.POSTED
                    RecurringOccurrence.Status.SKIPPED -> RecurringCycleStatus.SKIPPED
                },
                // The date the cycle was actually filed on, which is the transaction's
                // own for a confirmed one. The template's day is what it was *due* on,
                // and a cycle that has been answered is no longer described by that.
                date = occurrence.effectiveDate,
                occurrence = occurrence,
            )
        }

        return RecurringCycles(
            month = month,
            // Sorted before grouping, so every group comes out ordered by the cycle's own
            // date without each of them being sorted separately. Under any of the four
            // headings the creation order of the template says nothing: what orders a
            // pending cycle is how long ago it fell due, and an upcoming one how close it
            // is to falling due — the same rule, read from both ends.
            byStatus = (projected + handled).sortedBy { it.date }.groupBy { it.status },
        )
    }
}
