package com.neoutils.finsight.domain.error

enum class AccountError(val message: String) {
    EMPTY_NAME(message = "Account name cannot be empty"),
    ALREADY_EXIST(message = "Account name already exists"),
    NOT_FOUND(message = "Account not found"),
    CANNOT_DELETE_DEFAULT(message = "Cannot delete default account"),
}
