package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.RetireError
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CategoryRetirability
import com.neoutils.finsight.domain.model.SystemCategoryKey
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository

/**
 * Resolves, in one place, whether a category may be deleted or must be archived. The
 * four guards — movement on its dimension, a budget, a recurring, an account that
 * declares it yields — each name their own [RetireError], so [DeleteCategoryUseCase]
 * and the view can consume one decision instead of re-deriving it. One owner decides;
 * consumers only read.
 *
 * The yield guard is a dependent like any other, not a state of immutability: being a
 * system category confers no protection by itself, and the last account to stop
 * yielding makes the category ordinary again (design D4). Nothing here adds a third
 * outcome to the delete-vs-archive pair, so no screen learns a new case.
 */
class ResolveCategoryRetirabilityUseCase(
    private val entryRepository: IEntryRepository,
    private val budgetRepository: IBudgetRepository,
    private val recurringRepository: IRecurringRepository,
    private val accountRepository: IAccountRepository,
) {
    suspend operator fun invoke(category: Category): CategoryRetirability = when {
        entryRepository.hasEntriesForDimension(category.dimensionId) ->
            CategoryRetirability.MustArchive(RetireError.HAS_TRANSACTIONS)

        budgetRepository.hasBudgetForCategory(category.id) ->
            CategoryRetirability.MustArchive(RetireError.HAS_BUDGET)

        recurringRepository.hasRecurringForCategory(category.id) ->
            CategoryRetirability.MustArchive(RetireError.HAS_RECURRING)

        category.systemKey == SystemCategoryKey.YIELD && accountRepository.hasYieldingAccount() ->
            CategoryRetirability.MustArchive(RetireError.HAS_YIELDING_ACCOUNTS)

        else -> CategoryRetirability.Deletable
    }
}
