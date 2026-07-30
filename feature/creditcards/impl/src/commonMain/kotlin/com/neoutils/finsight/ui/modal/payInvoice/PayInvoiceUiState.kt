package com.neoutils.finsight.ui.modal.payInvoice

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.usecase.ConversionSuggestion

data class PayInvoiceUiState(
    val accounts: List<Account> = emptyList(),
    val selectedAccount: Account? = null,
    /** The currency the debt is denominated in — the card's, never the paying account's. */
    val cardCurrency: String? = null,
    /** What the app would put in the second field, when it knows a rate. */
    val suggestion: ConversionSuggestion? = null,
) {

    /**
     * Whether paying this invoice crosses currencies, which is the one condition that reveals
     * the editable amount. Derived from the account and the card, so it cannot disagree.
     */
    val isCrossCurrency: Boolean
        get() = selectedAccount != null &&
            cardCurrency != null &&
            selectedAccount.currency != cardCurrency
}
