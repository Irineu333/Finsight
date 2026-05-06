package com.neoutils.finsight.feature.installments.modal.addInstallment

import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.creditCards.model.InvoiceMonth

data class AddInstallmentUiState(
    val categories: List<Category> = emptyList(),
    val creditCards: List<CreditCard> = emptyList(),
    val selectedCreditCard: CreditCard? = null,
    val invoiceSelection: InvoiceMonth? = null,
) {
    val isInvoiceBlocked = invoiceSelection?.isBlocked == true
}
