package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Budget

/**
 * Removes a budget.
 *
 * A budget owns no money and no entry: it is a lens over spending the categories
 * already classify, so removing it takes nothing with it and there is no dependent to
 * guard against. The rows that link it to its categories go with it, by the cascade
 * `budget_categories` declares.
 */
interface DeleteBudgetUseCase {

    /**
     * The canonical form, and the one that carries the implementation.
     *
     * The budget is resolved **when the operation runs**, so the removal lands on the
     * budget as it is at that instant rather than as a caller once loaded it; an
     * identity that matches nothing is refused with `BudgetError.NOT_FOUND` and nothing
     * is removed.
     */
    suspend operator fun invoke(budgetId: Long): Either<Throwable, Unit>

    /**
     * The convenience for a caller that already holds the budget. It extracts the
     * identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(budget: Budget): Either<Throwable, Unit> = invoke(budget.id)
}
