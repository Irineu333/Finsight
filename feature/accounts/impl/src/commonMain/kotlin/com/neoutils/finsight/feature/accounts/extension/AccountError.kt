package com.neoutils.finsight.feature.accounts.extension

import com.neoutils.finsight.feature.accounts.error.AccountError
import com.neoutils.finsight.feature.accounts.resources.Res
import com.neoutils.finsight.feature.accounts.resources.account_error_already_exist
import com.neoutils.finsight.feature.accounts.resources.account_error_empty_name
import com.neoutils.finsight.feature.accounts.resources.account_error_not_found
import com.neoutils.finsight.core.ui.util.UiText
fun AccountError.toUiText() = when (this) {
    AccountError.EMPTY_NAME -> UiText.Res(Res.string.account_error_empty_name)
    AccountError.ALREADY_EXIST -> UiText.Res(Res.string.account_error_already_exist)
    AccountError.NOT_FOUND -> UiText.Res(Res.string.account_error_not_found)
    AccountError.CANNOT_DELETE_DEFAULT -> UiText.Raw(AccountError.CANNOT_DELETE_DEFAULT.message)
}
