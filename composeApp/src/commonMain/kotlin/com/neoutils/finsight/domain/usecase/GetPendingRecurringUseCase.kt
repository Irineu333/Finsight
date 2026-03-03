package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.extension.effectiveDay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth

class GetPendingRecurringUseCase {
    operator fun invoke(
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
            today.yearMonth.effectiveDay(recurring.dayOfMonth) <= today.day &&
                recurring.id !in handledRecurringIds
        }
    }
}
