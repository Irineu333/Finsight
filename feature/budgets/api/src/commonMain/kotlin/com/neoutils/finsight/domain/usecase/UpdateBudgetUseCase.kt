package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.LimitType
import com.neoutils.finsight.domain.model.Recurring

/**
 * Edits a budget — what it watches, what it is called and what it is measured against,
 * which is everything about it that is not identity, denomination or history.
 *
 * The currency is absent from the signature on purpose: a limit is denominated once, at
 * creation, and re-denominating it would silently rewrite the meaning of a number the
 * user typed (design D12/D13). Changing it is creating another budget.
 *
 * Validation, trimming, the derivation of a `PERCENTAGE` limit's amount and the
 * clearing of the fields a `FIXED` limit does not have live here, not in the caller —
 * and the uniqueness check ignores the budget's own identity, so an edit may keep its
 * title.
 */
interface UpdateBudgetUseCase {

    /**
     * The budget is resolved **when the operation runs**, so the edit lands on the
     * budget as it is at that instant and everything the caller did not name — its
     * currency, its creation instant — survives untouched; an identity that matches
     * nothing is refused with `BudgetError.NOT_FOUND` and nothing is written.
     *
     * @param amount the limit itself, read **only** when [limitType] is
     * [LimitType.FIXED].
     * @param percentage the share of [baseIncome] the limit is, read **only** when
     * [limitType] is [LimitType.PERCENTAGE]. A share nobody stated is zero.
     * @param baseIncome the recurring income a `PERCENTAGE` limit is a share of.
     * Required for that kind of limit and refused with
     * `BudgetError.MISSING_BASE_INCOME` when it is absent; ignored for a `FIXED` one.
     */
    suspend operator fun invoke(
        budgetId: Long,
        title: String,
        categories: List<Category>,
        iconKey: String,
        limitType: LimitType,
        amount: Double = 0.0,
        percentage: Double? = null,
        baseIncome: Recurring? = null,
    ): Either<Throwable, Unit>
}
