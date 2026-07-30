package com.neoutils.finsight.ui.modal.advancePayment

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.usecase.ConversionSuggestion

data class AdvancePaymentUiState(
    val accounts: List<Account> = emptyList(),
    val selectedAccount: Account? = null,
    /** The currency the invoice is denominated in — the card's, never the account's. */
    val cardCurrency: String? = null,
    /** What the app would put in the second field, when it knows a rate. */
    val suggestion: ConversionSuggestion? = null,
) {

    val isCrossCurrency: Boolean
        get() = selectedAccount != null &&
            cardCurrency != null &&
            selectedAccount.currency != cardCurrency
}
