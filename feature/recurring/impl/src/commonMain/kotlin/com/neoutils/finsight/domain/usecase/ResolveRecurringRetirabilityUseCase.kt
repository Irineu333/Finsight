package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.RecurringRetireError
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringRetirability
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository

/**
 * Resolves, in one place, whether a recurring may be deleted or must be archived.
 * The two guards — a transaction naming it, a budget pointing at it — each name their
 * own [RecurringRetireError], so [DeleteRecurringUseCase] and the view consume one
 * decision instead of re-deriving it. One owner decides; consumers only read.
 *
 * Occurrences are deliberately not a guard. A skipped cycle writes no transaction,
 * produces no entry and moves no money — refusing removal because of one would refuse
 * the merely inappropriate rather than what breaks an invariant (design D2).
 */
class ResolveRecurringRetirabilityUseCase(
    private val recurringRepository: IRecurringRepository,
    private val budgetRepository: IBudgetRepository,
) {
    suspend operator fun invoke(recurring: Recurring): RecurringRetirability = when {
        recurringRepository.hasTransactionForRecurring(recurring.id) ->
            RecurringRetirability.MustArchive(RecurringRetireError.HAS_TRANSACTIONS)

        budgetRepository.hasBudgetForRecurring(recurring.id) ->
            RecurringRetirability.MustArchive(RecurringRetireError.HAS_BUDGET)

        else -> RecurringRetirability.Deletable
    }
}
