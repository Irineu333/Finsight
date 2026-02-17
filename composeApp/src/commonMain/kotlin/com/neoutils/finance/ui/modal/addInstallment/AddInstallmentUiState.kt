package com.neoutils.finance.ui.modal.addInstallment

import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.InvoiceMonthSelection

data class AddInstallmentUiState(
    val categories: List<Category> = emptyList(),
    val creditCards: List<CreditCard> = emptyList(),
    val selectedCreditCard: CreditCard? = null,
    val invoiceSelection: InvoiceMonthSelection? = null,
) {
    val isInvoiceBlocked = invoiceSelection?.isBlocked == true
}
