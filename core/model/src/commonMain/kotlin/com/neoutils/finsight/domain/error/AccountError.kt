package com.neoutils.finsight.domain.error

import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.account_error_adjustment_date_in_future
import com.neoutils.finsight.resources.account_error_already_exist
import com.neoutils.finsight.resources.account_error_cannot_archive_default
import com.neoutils.finsight.resources.account_error_cannot_delete_default
import com.neoutils.finsight.resources.account_error_currency_immutable
import com.neoutils.finsight.resources.account_error_empty_name
import com.neoutils.finsight.resources.account_error_has_balance
import com.neoutils.finsight.resources.account_error_has_recurring
import com.neoutils.finsight.resources.account_error_has_transactions
import com.neoutils.finsight.resources.account_error_not_found
import com.neoutils.finsight.util.UiText

enum class AccountError(val message: String) {
    EMPTY_NAME(message = "Account name cannot be empty"),
    ALREADY_EXIST(message = "Account name already exists"),
    NOT_FOUND(message = "Account not found"),
    CANNOT_DELETE_DEFAULT(message = "Cannot delete default account"),
    CANNOT_ARCHIVE_DEFAULT(message = "Cannot archive default account"),

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

    /**
     * The recurring FKs are SET_NULL: deleting would strip the link instead of
     * failing, leaving a template with nothing to post through. Refused here so
     * the orphan is never created, rather than remedied afterwards.
     */
    HAS_RECURRING(message = "Cannot delete an account a recurring transaction still uses"),

    /**
     * The currency is fixed when the account is created and never changes — not "until
     * the first entry", but from the instant the account exists (design D12).
     *
     * The refusal reads **no fact at all**, and that is the point: currency is an
     * attribute of identity, not of history, so a rule that consulted `hasEntries` would
     * be a conditional refusal — one somebody has to remember to keep correct. The path
     * to fixing a wrong choice already exists and is not new: an account with no entries
     * may be deleted and created again. One with entries has no correction possible in
     * any design, because the meaning of every entry already written depends on it.
     */
    CURRENCY_IS_IMMUTABLE(message = "An account's currency cannot be changed"),

    /**
     * A balance is `Σ entries` up to a date, so an adjustment dated ahead of today
     * corrects a reading nobody can take yet: the difference it posts is measured
     * against that future balance, while every screen goes on showing the old figure —
     * the correction lands in a month the user never opens and reads as having done
     * nothing.
     *
     * The refusal lives here and not on the form. The date picker stops at today, but
     * that is a convenience of one screen; every other way in — the agent surface among
     * them — reaches the operation without it.
     */
    ADJUSTMENT_DATE_IN_FUTURE(message = "An adjustment cannot be dated in the future"),
}

fun AccountError.toUiText() = when (this) {
    AccountError.EMPTY_NAME -> UiText.Res(Res.string.account_error_empty_name)
    AccountError.ALREADY_EXIST -> UiText.Res(Res.string.account_error_already_exist)
    AccountError.NOT_FOUND -> UiText.Res(Res.string.account_error_not_found)
    AccountError.CANNOT_DELETE_DEFAULT -> UiText.Res(Res.string.account_error_cannot_delete_default)
    AccountError.CANNOT_ARCHIVE_DEFAULT -> UiText.Res(Res.string.account_error_cannot_archive_default)
    AccountError.HAS_TRANSACTIONS -> UiText.Res(Res.string.account_error_has_transactions)
    AccountError.HAS_BALANCE -> UiText.Res(Res.string.account_error_has_balance)
    AccountError.HAS_RECURRING -> UiText.Res(Res.string.account_error_has_recurring)
    AccountError.CURRENCY_IS_IMMUTABLE -> UiText.Res(Res.string.account_error_currency_immutable)
    AccountError.ADJUSTMENT_DATE_IN_FUTURE ->
        UiText.Res(Res.string.account_error_adjustment_date_in_future)
}