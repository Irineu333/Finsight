package com.neoutils.finsight.domain.error

enum class CreditCardError(val message: String) {
    EMPTY_NAME(message = "Credit card name is required"),
    ALREADY_EXIST_NAME(message = "Credit card name already exists"),
    NEGATIVE_LIMIT(message = "Credit card limit cannot be negative"),
    MISSING_CLOSING_DAY(message = "Closing day is required"),
    INVALID_CLOSING_DAY(message = "Closing day must be between 1 and 31"),
    MISSING_DUE_DAY(message = "Due day is required"),
    INVALID_DUE_DAY(message = "Due day must be between 1 and 31"),
    NOT_FOUND(message = "Credit card not found"),
}
