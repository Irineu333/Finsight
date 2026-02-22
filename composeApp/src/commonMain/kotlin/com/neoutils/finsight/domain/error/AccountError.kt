package com.neoutils.finsight.domain.error

import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.account_error_already_exist
import com.neoutils.finsight.resources.account_error_empty_name
import com.neoutils.finsight.resources.account_error_not_found
import com.neoutils.finsight.util.UiText

enum class AccountError(val message: String) {
    EMPTY_NAME(message = "Account name cannot be empty"),
    ALREADY_EXIST(message = "Account name already exists"),
    NOT_FOUND(message = "Account not found"),
    CANNOT_DELETE_DEFAULT(message = "Cannot delete default account"),
}

fun AccountError.toUiText() = when (this) {
    AccountError.EMPTY_NAME -> UiText.Res(Res.string.account_error_empty_name)
    AccountError.ALREADY_EXIST -> UiText.Res(Res.string.account_error_already_exist)
    AccountError.NOT_FOUND -> UiText.Res(Res.string.account_error_not_found)
    AccountError.CANNOT_DELETE_DEFAULT -> UiText.Raw(AccountError.CANNOT_DELETE_DEFAULT.message)
}