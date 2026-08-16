package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.BudgetError
import com.neoutils.finsight.domain.exception.BudgetException
import com.neoutils.finsight.domain.repository.IBudgetRepository

class DeleteBudgetUseCaseImpl(
    private val budgetRepository: IBudgetRepository,
) : DeleteBudgetUseCase {

    override suspend fun invoke(budgetId: Long): Either<Throwable, Unit> = either {
        // Resolved here and not received: removal is irreversible, so the budget has to
        // be the one that exists now — a screen that loaded it minutes ago may be
        // holding a budget already gone.
        val budget = ensureNotNull(catch { budgetRepository.getBudgetById(budgetId) }.bind()) {
            BudgetException(BudgetError.NOT_FOUND)
        }

        catch { budgetRepository.delete(budget) }.bind()
    }
}
