package com.neoutils.finsight.feature.recurring.usecase

import com.neoutils.finsight.core.domain.model.Recurring
import com.neoutils.finsight.core.domain.model.RecurringOccurrence
import kotlinx.datetime.LocalDate

interface IGetPendingRecurringUseCase {
    operator fun invoke(
        recurringList: List<Recurring>,
        occurrences: List<RecurringOccurrence>,
        today: LocalDate,
    ): List<Recurring>
}
