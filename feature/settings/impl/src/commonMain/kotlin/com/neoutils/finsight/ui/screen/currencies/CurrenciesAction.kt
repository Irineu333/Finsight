package com.neoutils.finsight.ui.screen.currencies

sealed interface CurrenciesAction {

    data class Archive(val code: String) : CurrenciesAction

    data class Unarchive(val code: String) : CurrenciesAction

    data class Delete(val code: String) : CurrenciesAction

    /** The refusal has been read; the screen stops stating it. */
    data object DismissError : CurrenciesAction
}
