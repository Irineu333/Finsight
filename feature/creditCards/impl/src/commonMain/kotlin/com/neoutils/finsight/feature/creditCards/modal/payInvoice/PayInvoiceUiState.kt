package com.neoutils.finsight.feature.creditCards.modal.payInvoice

import com.neoutils.finsight.feature.accounts.model.Account
import com.neoutils.finsight.feature.creditCards.model.form.PayInvoiceForm

sealed interface PayInvoiceUiState {
    data object Loading : PayInvoiceUiState
    data object Error : PayInvoiceUiState
    data class Content(
        val form: PayInvoiceForm,
        val accounts: List<Account>,
    ) : PayInvoiceUiState
}
