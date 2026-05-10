package com.neoutils.finsight.feature.creditCards.modal.editInvoiceBalance

import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.creditCards.model.Invoice

sealed interface EditInvoiceBalanceUiState {
    data object Loading : EditInvoiceBalanceUiState
    data object Error : EditInvoiceBalanceUiState
    data class Content(
        val creditCards: List<CreditCard>,
        val selectedCreditCard: CreditCard,
        val editableInvoices: List<Invoice>,
        val selectedInvoice: Invoice,
        val currentBalance: Double,
    ) : EditInvoiceBalanceUiState
}