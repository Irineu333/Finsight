package com.neoutils.finsight.domain.error

import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.account_error_already_exist
import com.neoutils.finsight.resources.account_error_empty_name
import com.neoutils.finsight.resources.account_error_has_balance
import com.neoutils.finsight.resources.account_error_has_transactions
import com.neoutils.finsight.resources.account_error_not_found
import com.neoutils.finsight.util.UiText

enum class AccountError(val message: String) {
    EMPTY_NAME(message = "Account name cannot be empty"),
    ALREADY_EXIST(message = "Account name already exists"),
    NOT_FOUND(message = "Account not found"),
    CANNOT_DELETE_DEFAULT(message = "Cannot delete default account"),

    /**
     * Deleting would break the entries that reference the account. The action the
     * user wants is to close it, which preserves them.
     */
    HAS_TRANSACTIONS(message = "Cannot delete an account that has transactions"),

    /**
     * Closing does not invent a transaction to zero the balance: that would put a
     * movement the user never made into their history, in place of the one fact
     * only they have — where the money actually went.
     */
    HAS_BALANCE(message = "Cannot close an account whose balance is not zero"),
}

fun AccountError.toUiText() = when (this) {
    AccountError.EMPTY_NAME -> UiText.Res(Res.string.account_error_empty_name)
    AccountError.ALREADY_EXIST -> UiText.Res(Res.string.account_error_already_exist)
    AccountError.NOT_FOUND -> UiText.Res(Res.string.account_error_not_found)
    AccountError.CANNOT_DELETE_DEFAULT -> UiText.Raw(AccountError.CANNOT_DELETE_DEFAULT.message)
    AccountError.HAS_TRANSACTIONS -> UiText.Res(Res.string.account_error_has_transactions)
    AccountError.HAS_BALANCE -> UiText.Res(Res.string.account_error_has_balance)
}