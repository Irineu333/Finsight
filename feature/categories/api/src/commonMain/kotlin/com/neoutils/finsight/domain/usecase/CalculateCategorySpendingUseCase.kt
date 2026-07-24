package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.CategorySpending
import kotlinx.datetime.YearMonth

/** Spending per expense category in a month, derived from Σ entries of each category account. */
interface CalculateCategorySpendingUseCase {
    suspend operator fun invoke(forYearMonth: YearMonth): List<CategorySpending>
}
