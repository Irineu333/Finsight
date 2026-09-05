package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.RecurringRetireError
import com.neoutils.finsight.domain.model.RecurringRetirability
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository

class ResolveRecurringRetirabilityUseCaseImpl(
    private val recurringRepository: IRecurringRepository,
    private val budgetRepository: IBudgetRepository,
) : ResolveRecurringRetirabilityUseCase {

    override suspend fun invoke(recurringId: Long): RecurringRetirability = when {
        recurringRepository.hasTransactionForRecurring(recurringId) ->
            RecurringRetirability.MustArchive(RecurringRetireError.HAS_TRANSACTIONS)

        budgetRepository.hasBudgetForRecurring(recurringId) ->
            RecurringRetirability.MustArchive(RecurringRetireError.HAS_BUDGET)

        else -> RecurringRetirability.Deletable
    }
}
