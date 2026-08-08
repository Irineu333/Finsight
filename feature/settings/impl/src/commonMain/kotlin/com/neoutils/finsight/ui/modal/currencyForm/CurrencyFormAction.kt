package com.neoutils.finsight.ui.modal.currencyForm

sealed interface CurrencyFormAction {

    /**
     * Typing the code is what makes the platform suggest a symbol and a name. It
     * suggests; it never decides — the user may replace either, and a code the platform
     * does not recognise is still perfectly registrable.
     */
    data class ChangeCode(val code: String) : CurrencyFormAction

    data class ChangeSymbol(val symbol: String) : CurrencyFormAction

    data class ChangeName(val name: String) : CurrencyFormAction

    data object Submit : CurrencyFormAction
}
