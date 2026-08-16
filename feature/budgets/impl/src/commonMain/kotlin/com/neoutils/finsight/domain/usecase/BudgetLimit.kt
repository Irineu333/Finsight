package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.neoutils.finsight.domain.error.BudgetError
import com.neoutils.finsight.domain.exception.BudgetException
import com.neoutils.finsight.domain.model.LimitType
import com.neoutils.finsight.domain.model.Recurring

/**
 * The three fields a budget stores its limit in, made coherent with the kind of limit
 * that was asked for.
 *
 * It exists so creating and editing answer the same way: a caller states *what kind of
 * limit* it wants and the two things that could describe one, and the shape that gets
 * stored is decided here, once.
 */
internal data class BudgetLimit(
    val amount: Double,
    val percentage: Double?,
    val recurringId: Long?,
)

/**
 * Resolves the limit a budget is measured against.
 *
 * A `PERCENTAGE` limit is a **share of a recurring income**, so its amount is derived
 * from the share and that income rather than handed in — the same derivation
 * [CalculateBudgetProgressUseCase] reads it back with, and the reason no caller is
 * asked to compute it. A share nobody stated is zero, which is also the answer the
 * progress gives for it; a base income nobody named is refused, because a share of
 * nothing is not a limit.
 *
 * A `FIXED` limit carries neither share nor base income: leaving them behind would
 * make the budget read as a fraction of something the user had already stopped
 * measuring against.
 */
internal fun budgetLimit(
    limitType: LimitType,
    amount: Double,
    percentage: Double?,
    baseIncome: Recurring?,
): Either<BudgetException, BudgetLimit> = when (limitType) {
    LimitType.FIXED -> BudgetLimit(
        amount = amount,
        percentage = null,
        recurringId = null,
    ).right()

    LimitType.PERCENTAGE -> baseIncome?.let {
        BudgetLimit(
            amount = it.amount * (percentage ?: 0.0) / 100.0,
            percentage = percentage,
            recurringId = it.id,
        ).right()
    } ?: BudgetException(BudgetError.MISSING_BASE_INCOME).left()
}
