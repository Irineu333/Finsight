package com.neoutils.finsight.domain.error

import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.budget_error_already_exist
import com.neoutils.finsight.resources.budget_error_empty_title
import com.neoutils.finsight.resources.budget_error_missing_base_income
import com.neoutils.finsight.resources.budget_error_not_found
import com.neoutils.finsight.util.UiText

enum class BudgetError(val message: String) {
    EMPTY_TITLE(message = "Budget title cannot be empty"),
    ALREADY_EXIST(message = "Budget title already exists"),

    /**
     * The identity handed to the operation matches no budget. Every use case resolves
     * the budget when it runs, so this is the refusal a caller gets for an identifier
     * that was never valid — and for one that stopped being valid between the moment
     * it was read and the moment it was used.
     */
    NOT_FOUND(message = "Budget not found"),

    /**
     * A `PERCENTAGE` limit was asked for without the recurring income it is a share of.
     *
     * A share of nothing is not a limit, and there is no sensible number to store: the
     * budget would read as zero forever. An **omitted percentage** is a different case
     * and is not refused — a share nobody stated is zero, which is the same answer the
     * progress reads back for it.
     */
    MISSING_BASE_INCOME(message = "A percentage budget needs the recurring income it is a share of"),
}

fun BudgetError.toUiText() = when (this) {
    BudgetError.EMPTY_TITLE -> UiText.Res(Res.string.budget_error_empty_title)
    BudgetError.ALREADY_EXIST -> UiText.Res(Res.string.budget_error_already_exist)
    BudgetError.NOT_FOUND -> UiText.Res(Res.string.budget_error_not_found)
    BudgetError.MISSING_BASE_INCOME -> UiText.Res(Res.string.budget_error_missing_base_income)
}
