package com.neoutils.finsight.domain.error

enum class BudgetError(val message: String) {
    EMPTY_TITLE(message = "Budget title cannot be empty"),
    ALREADY_EXIST(message = "Budget title already exists"),
}
