package com.neoutils.finsight.feature.budgets.error

enum class BudgetError(val message: String) {
    EMPTY_TITLE(message = "Budget title cannot be empty"),
    ALREADY_EXIST(message = "Budget title already exists"),
    NOT_FOUND(message = "Budget not found"),
}
