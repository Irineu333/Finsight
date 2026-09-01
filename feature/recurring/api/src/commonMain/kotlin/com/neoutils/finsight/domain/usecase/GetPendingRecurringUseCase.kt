package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth

/**
 * The templates of [today]'s month whose cycle is **pending** — nothing recorded for it,
 * and its date already come.
 *
 * It is a question put to [GetRecurringCyclesUseCase] and not a predicate of its own.
 * "Pending" is one of the four states of a cycle, the partition owns all four, and a
 * second definition here would be free to drift from the one the recurring screen reads —
 * which is precisely what happened while the cut was written as a comparison of days.
 *
 * The shape stays a list of templates because that is what its caller asks about: the
 * dashboard names the recurrings the user still owes an answer to, not their cycles.
 */
class GetPendingRecurringUseCase(
    private val getRecurringCycles: GetRecurringCyclesUseCase,
) {
    operator fun invoke(
        recurringList: List<Recurring>,
        occurrences: List<RecurringOccurrence>,
        today: LocalDate,
    ): List<Recurring> = getRecurringCycles(
        recurringList = recurringList,
        occurrences = occurrences,
        month = today.yearMonth,
        today = today,
    ).pending.map { it.recurring }
}
