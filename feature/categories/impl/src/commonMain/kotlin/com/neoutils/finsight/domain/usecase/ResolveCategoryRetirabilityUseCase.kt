package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.RetireError
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CategoryRetirability
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository

/**
 * Resolves, in one place, whether a category may be deleted or must be archived. The
 * three guards — movement on its dimension, a budget, a recurring — each name their
 * own [RetireError], so [DeleteCategoryUseCase] and the view can consume one decision
 * instead of re-deriving it. One owner decides; consumers only read.
 */
class ResolveCategoryRetirabilityUseCase(
    private val entryRepository: IEntryRepository,
    private val budgetRepository: IBudgetRepository,
    private val recurringRepository: IRecurringRepository,
) {
    suspend operator fun invoke(category: Category): CategoryRetirability = when {
        entryRepository.hasEntriesForDimension(category.dimensionId) ->
            CategoryRetirability.MustArchive(RetireError.HAS_TRANSACTIONS)

        budgetRepository.hasBudgetForCategory(category.id) ->
            CategoryRetirability.MustArchive(RetireError.HAS_BUDGET)

        recurringRepository.hasRecurringForCategory(category.id) ->
            CategoryRetirability.MustArchive(RetireError.HAS_RECURRING)

        else -> CategoryRetirability.Deletable
    }
}
