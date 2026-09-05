package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.LimitType
import com.neoutils.finsight.domain.model.Recurring

/**
 * Creates one of the user's budgets — a lens over the spending of [categories], with a
 * limit to read it against.
 *
 * It owns the four things a caller must not decide for itself: the title is validated
 * (non-blank, and unique across the user's budgets) and stored trimmed, the limit's
 * amount is derived from the kind of limit that was asked for, the fields that only a
 * `PERCENTAGE` limit has are cleared when the limit is `FIXED`, and the creation
 * instant is read here — a caller that supplied it could date a budget before or after
 * it existed.
 *
 * It takes no identity: there is nothing to resolve, since the budget it operates on is
 * the one it brings into existence. [currency] is stated once, at creation, and is
 * never revisited — see [UpdateBudgetUseCase].
 *
 * It answers the budget as stored, identity included, because a caller that cannot name
 * what it just created cannot report it either — a surface answering a request from
 * outside has nothing else to point the requester at.
 */
interface CreateBudgetUseCase {

    /**
     * @param currency what the limit is denominated in. A category is a dimension and
     * has no currency of its own, so the denomination cannot be derived from what the
     * budget measures: it is declared, once, and immutable from then on (design D13).
     * @param amount the limit itself, read **only** when [limitType] is
     * [LimitType.FIXED].
     * @param percentage the share of [baseIncome] the limit is, read **only** when
     * [limitType] is [LimitType.PERCENTAGE]. A share nobody stated is zero.
     * @param baseIncome the recurring income a `PERCENTAGE` limit is a share of.
     * Required for that kind of limit and refused with
     * `BudgetError.MISSING_BASE_INCOME` when it is absent; ignored for a `FIXED` one.
     */
    suspend operator fun invoke(
        title: String,
        categories: List<Category>,
        iconKey: String,
        currency: String,
        limitType: LimitType,
        amount: Double = 0.0,
        percentage: Double? = null,
        baseIncome: Recurring? = null,
    ): Either<Throwable, Budget>
}
