package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import kotlinx.datetime.YearMonth

/**
 * The templates [month] still has nothing recorded for: not archived, in a month the
 * series has already begun, and with no occurrence — confirmed or skipped — in that month.
 *
 * It is the predicate alone, without any cut by day: whoever asks decides whether the
 * cycle's day matters. [GetPendingRecurringUseCase] adds that cut and calls the result
 * "pending"; a figure that looks at the whole month ahead reads this one directly.
 *
 * An unhandled template has no ledger entry by definition, which is what makes it
 * disjoint from anything the ledger already answers for.
 *
 * **A month before the series began has nothing to record, not something unrecorded.**
 * `Recurring.originMonth` is cycle 1, and a month before it holds a cycle the numbering
 * would call zero or less. Without the floor, asking about any past month answered with
 * every active template — which no consumer could see while the only caller asked about
 * the current month, and which a month selector makes reachable at once.
 */
class GetUnhandledRecurringUseCase {
    operator fun invoke(
        recurringList: List<Recurring>,
        occurrences: List<RecurringOccurrence>,
        month: YearMonth,
    ): List<Recurring> {
        val handledRecurringIds = occurrences
            .asSequence()
            .filter { it.yearMonth == month }
            .map { it.recurringId }
            .toSet()

        return recurringList.filter { recurring ->
            recurring.generatesCycleIn(month) && recurring.id !in handledRecurringIds
        }
    }
}
