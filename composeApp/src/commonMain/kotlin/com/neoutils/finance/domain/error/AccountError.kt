package com.neoutils.finance.domain.error

import com.neoutils.finance.resources.Res
import com.neoutils.finance.resources.account_error_already_exist
import com.neoutils.finance.resources.account_error_empty_name
import com.neoutils.finance.resources.account_error_not_found
import com.neoutils.finance.util.UiText

enum class AccountError(val message: String) {
    EMPTY_NAME(message = "Account name cannot be empty"),
    ALREADY_EXIST(message = "Account name already exists"),
    NOT_FOUND(message = "Account not found"),
}

fun AccountError.toUiText() = when (this) {
    AccountError.EMPTY_NAME -> UiText.Res(Res.string.account_error_empty_name)
    AccountError.ALREADY_EXIST -> UiText.Res(Res.string.account_error_already_exist)
    AccountError.NOT_FOUND -> UiText.Res(Res.string.account_error_not_found)
}