package com.neoutils.finsight.domain.error

enum class RecurringError(val message: String) {
    AMOUNT_REQUIRED(message = "Amount is required."),
    AMOUNT_ZERO(message = "Amount cannot be zero."),
    TITLE_OR_CATEGORY_REQUIRED(message = "Title or category is required."),
    INVALID_DAY(message = "Day of month must be between 1 and 31."),
    ACCOUNT_REQUIRED(message = "Account is required."),
}
