package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.extension.effectiveDay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth

class GetPendingRecurringUseCase {
    operator fun invoke(
        recurringList: List<Recurring>,
        today: LocalDate,
    ): List<Recurring> {
        return recurringList.filter { recurring ->
            today.yearMonth.effectiveDay(recurring.dayOfMonth) <= today.day &&
                recurring.lastHandledYearMonth != today.yearMonth
        }
    }
}
