package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.neoutils.finsight.domain.error.GoalError
import com.neoutils.finsight.domain.repository.IGoalRepository

class ValidateGoalTitleUseCase(
    private val repository: IGoalRepository,
) {
    suspend operator fun invoke(
        title: String,
        ignoreId: Long? = null,
    ): Either<GoalError, String> {
        if (title.isBlank()) {
            return GoalError.EMPTY_TITLE.left()
        }

        if (hasDuplicateTitle(title, ignoreId)) {
            return GoalError.ALREADY_EXIST.left()
        }

        return title.right()
    }

    private suspend fun hasDuplicateTitle(
        title: String,
        ignoreId: Long?,
    ): Boolean {
        return repository.getAllGoals().any { goal ->
            goal.title.equals(title.trim(), ignoreCase = true) &&
                goal.id != ignoreId
        }
    }
}
