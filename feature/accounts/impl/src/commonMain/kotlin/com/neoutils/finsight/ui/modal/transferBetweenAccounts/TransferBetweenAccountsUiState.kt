package com.neoutils.finsight.ui.modal.transferBetweenAccounts

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.usecase.ConversionSuggestion

data class TransferBetweenAccountsUiState(
    val accounts: List<Account> = emptyList(),
    val destinationAccounts: List<Account> = emptyList(),
    val selectedSourceAccount: Account? = null,
    val selectedDestinationAccount: Account? = null,
    /**
     * What the app would put in the second field, when it knows a rate. `null` covers both
     * "nothing to convert" and "no rate known", which the form treats the same way: the user
     * types the amount, and the operation collects the rate that was missing.
     */
    val suggestion: ConversionSuggestion? = null,
) {

    /**
     * Whether the operation crosses currencies — the one condition that reveals the second
     * field. Derived from the two accounts rather than stored, so it cannot disagree with them.
     */
    val isCrossCurrency: Boolean
        get() = selectedSourceAccount != null &&
            selectedDestinationAccount != null &&
            selectedSourceAccount.currency != selectedDestinationAccount.currency
}
