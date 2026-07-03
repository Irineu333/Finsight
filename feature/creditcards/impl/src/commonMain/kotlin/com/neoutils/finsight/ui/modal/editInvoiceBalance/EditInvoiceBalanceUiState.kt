package com.neoutils.finsight.ui.modal.editInvoiceBalance

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice

sealed interface EditInvoiceBalanceUiState {
    data object Loading : EditInvoiceBalanceUiState
    data class Content(
        val creditCards: List<CreditCard>,
        val selectedCreditCard: CreditCard,
        val editableInvoices: List<Invoice>,
        val selectedInvoice: Invoice,
        val currentBalance: Double,
    ) : EditInvoiceBalanceUiState
}