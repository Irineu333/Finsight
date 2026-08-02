package com.neoutils.finsight.ui.modal.currencyForm

import com.neoutils.finsight.util.UiText

data class CurrencyFormUiState(
    val code: String = "",
    val symbol: String = "",
    val name: String = "",
    /**
     * An existing row is edited, never re-coded: the code is denormalised across
     * accounts, entries, budgets and rates, so changing it would be a data migration
     * rather than an edit.
     */
    val isEditing: Boolean = false,
    val error: UiText? = null,
) {
    /**
     * There is **no control for decimal places**, and there is none to add: every stored
     * currency has two. The premise belongs to the arithmetic of the whole app, not to
     * this form.
     */
    val canSubmit: Boolean get() = code.isNotBlank() && symbol.isNotBlank()
}
