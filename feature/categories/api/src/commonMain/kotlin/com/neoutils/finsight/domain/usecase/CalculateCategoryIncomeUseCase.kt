package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.CategorySpending
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/** Income per income category in a month, derived from Σ entries of each category account. */
interface CalculateCategoryIncomeUseCase {
    /**
     * [base] is the currency the figures are reduced to when a category's entries span more than
     * one, and [today] clamps the date whose rates govern that reduction. Both are arguments and
     * not fields: a figure is not consolidable without someone saying in what currency it is
     * read, and passing it makes forgetting to follow the preference a compile error rather than
     * a screen that quietly stops reacting.
     */
    suspend operator fun invoke(
        forYearMonth: YearMonth,
        base: String,
        today: LocalDate,
    ): List<CategorySpending>
}
