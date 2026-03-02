package com.neoutils.finsight.domain.error

import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.budget_error_already_exist
import com.neoutils.finsight.resources.budget_error_empty_title
import com.neoutils.finsight.util.UiText

enum class BudgetError(val message: String) {
    EMPTY_TITLE(message = "Budget title cannot be empty"),
    ALREADY_EXIST(message = "Budget title already exists"),
}

fun BudgetError.toUiText() = when (this) {
    BudgetError.EMPTY_TITLE -> UiText.Res(Res.string.budget_error_empty_title)
    BudgetError.ALREADY_EXIST -> UiText.Res(Res.string.budget_error_already_exist)
}
