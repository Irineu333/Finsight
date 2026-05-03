package com.neoutils.finsight.feature.recurring.usecase

import com.neoutils.finsight.core.domain.model.Recurring
import com.neoutils.finsight.core.domain.model.RecurringOccurrence
import com.neoutils.finsight.core.utils.extension.effectiveDay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth

class GetPendingRecurringUseCase : IGetPendingRecurringUseCase {
    override operator fun invoke(
        recurringList: List<Recurring>,
        occurrences: List<RecurringOccurrence>,
        today: LocalDate,
    ): List<Recurring> {
        val handledRecurringIds = occurrences
            .asSequence()
            .filter { it.yearMonth == today.yearMonth }
            .map { it.recurringId }
            .toSet()

        return recurringList.filter { recurring ->
            recurring.isActive &&
                today.yearMonth.effectiveDay(recurring.dayOfMonth) <= today.day &&
                recurring.id !in handledRecurringIds
        }
    }
}
