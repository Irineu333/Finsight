package com.neoutils.finsight.feature.budgets.extension

import com.neoutils.finsight.feature.budgets.error.BudgetError
import com.neoutils.finsight.feature.budgets.resources.Res
import com.neoutils.finsight.feature.budgets.resources.budget_error_already_exist
import com.neoutils.finsight.feature.budgets.resources.budget_error_empty_title
import com.neoutils.finsight.core.ui.util.UiText
fun BudgetError.toUiText() = when (this) {
    BudgetError.EMPTY_TITLE -> UiText.Res(Res.string.budget_error_empty_title)
    BudgetError.ALREADY_EXIST -> UiText.Res(Res.string.budget_error_already_exist)
}
