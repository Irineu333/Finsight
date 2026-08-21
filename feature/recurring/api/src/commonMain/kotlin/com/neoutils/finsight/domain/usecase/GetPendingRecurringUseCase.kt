package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.extension.effectiveDay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth

/**
 * The templates of [today]'s month that are still unhandled **and** whose effective day
 * has already come — what the app calls *pending*.
 *
 * Pending is [GetUnhandledRecurringUseCase] plus the cut by day, and nothing else: the
 * set of handled templates is not recomputed here, it is asked for.
 */
class GetPendingRecurringUseCase(
    private val getUnhandledRecurring: GetUnhandledRecurringUseCase,
) {
    operator fun invoke(
        recurringList: List<Recurring>,
        occurrences: List<RecurringOccurrence>,
        today: LocalDate,
    ): List<Recurring> = getUnhandledRecurring(
        recurringList = recurringList,
        occurrences = occurrences,
        month = today.yearMonth,
    ).filter { recurring ->
        today.yearMonth.effectiveDay(recurring.dayOfMonth) <= today.day
    }
}
