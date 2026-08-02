package com.neoutils.finsight.domain.error

import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.currency_error_base_currency_not_archivable
import com.neoutils.finsight.resources.currency_error_code_exists
import com.neoutils.finsight.resources.currency_error_code_required
import com.neoutils.finsight.resources.currency_error_denominated_by_account
import com.neoutils.finsight.resources.currency_error_denominated_by_budget
import com.neoutils.finsight.resources.currency_error_not_found
import com.neoutils.finsight.resources.currency_error_symbol_required
import com.neoutils.finsight.resources.currency_error_unsupported_decimals
import com.neoutils.finsight.util.UiText

/**
 * Why a currency could not be registered, archived or deleted.
 *
 * Every refusal here is **actionable**: the two that block a deletion name what
 * denominates the currency, so the user knows what to change — or that archiving is the
 * way out.
 */
enum class CurrencyError(val message: String) {
    CODE_REQUIRED(message = "Currency code cannot be empty"),
    SYMBOL_REQUIRED(message = "Currency symbol cannot be empty"),
    CODE_EXISTS(message = "A currency with this code already exists"),

    /**
     * The whole arithmetic of the app assumes base 100, so a currency of zero or three
     * decimal places is refused where a currency comes into existence — the premise is
     * applied here now that there is no curated list to exercise it by omission.
     */
    UNSUPPORTED_DECIMALS(message = "Currency does not have two decimal places"),

    DENOMINATED_BY_ACCOUNT(message = "An account is denominated in this currency"),
    DENOMINATED_BY_BUDGET(message = "A budget limit is denominated in this currency"),

    /**
     * Archiving the base would leave every consolidated figure denominated in a currency
     * the app declares it no longer offers. Switching the base is the way out.
     */
    BASE_CURRENCY_NOT_ARCHIVABLE(message = "The base currency cannot be archived"),

    NOT_FOUND(message = "Currency not found"),
}

fun CurrencyError.toUiText() = when (this) {
    CurrencyError.CODE_REQUIRED -> UiText.Res(Res.string.currency_error_code_required)
    CurrencyError.SYMBOL_REQUIRED -> UiText.Res(Res.string.currency_error_symbol_required)
    CurrencyError.CODE_EXISTS -> UiText.Res(Res.string.currency_error_code_exists)
    CurrencyError.UNSUPPORTED_DECIMALS -> UiText.Res(Res.string.currency_error_unsupported_decimals)
    CurrencyError.DENOMINATED_BY_ACCOUNT ->
        UiText.Res(Res.string.currency_error_denominated_by_account)

    CurrencyError.DENOMINATED_BY_BUDGET ->
        UiText.Res(Res.string.currency_error_denominated_by_budget)

    CurrencyError.BASE_CURRENCY_NOT_ARCHIVABLE ->
        UiText.Res(Res.string.currency_error_base_currency_not_archivable)

    CurrencyError.NOT_FOUND -> UiText.Res(Res.string.currency_error_not_found)
}
