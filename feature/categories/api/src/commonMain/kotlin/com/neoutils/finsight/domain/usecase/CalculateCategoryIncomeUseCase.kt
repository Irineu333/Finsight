package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.CategorySpending
import kotlinx.datetime.YearMonth

/** Income per income category in a month, derived from Σ entries of each category account. */
interface CalculateCategoryIncomeUseCase {
    suspend operator fun invoke(forYearMonth: YearMonth): List<CategorySpending>
}
