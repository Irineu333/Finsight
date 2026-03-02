package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.neoutils.finsight.domain.error.BudgetError
import com.neoutils.finsight.domain.repository.IBudgetRepository

class ValidateBudgetTitleUseCase(
    private val repository: IBudgetRepository,
) {
    suspend operator fun invoke(
        title: String,
        ignoreId: Long? = null,
    ): Either<BudgetError, String> {
        if (title.isBlank()) {
            return BudgetError.EMPTY_TITLE.left()
        }

        if (hasDuplicateTitle(title, ignoreId)) {
            return BudgetError.ALREADY_EXIST.left()
        }

        return title.right()
    }

    private suspend fun hasDuplicateTitle(
        title: String,
        ignoreId: Long?,
    ): Boolean {
        return repository.getAllBudgets().any { budget ->
            budget.title.equals(title.trim(), ignoreCase = true) &&
                budget.id != ignoreId
        }
    }
}
