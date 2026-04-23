package com.neoutils.finsight.extension

import com.neoutils.finsight.domain.error.BudgetError
import com.neoutils.finsight.feature.budgets.impl.resources.Res
import com.neoutils.finsight.feature.budgets.impl.resources.budget_error_already_exist
import com.neoutils.finsight.feature.budgets.impl.resources.budget_error_empty_title
import com.neoutils.finsight.util.UiText

fun BudgetError.toUiText() = when (this) {
    BudgetError.EMPTY_TITLE -> UiText.Res(Res.string.budget_error_empty_title)
    BudgetError.ALREADY_EXIST -> UiText.Res(Res.string.budget_error_already_exist)
}
