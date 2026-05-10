package com.neoutils.finsight.feature.transactions.error

enum class OperationError(val message: String) {
    NOT_FOUND(message = "Operation not found."),
    PERSPECTIVE_MISMATCH(message = "Operation does not match the requested perspective."),
}
