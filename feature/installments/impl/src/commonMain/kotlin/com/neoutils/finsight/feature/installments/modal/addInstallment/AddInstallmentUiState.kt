package com.neoutils.finsight.feature.installments.modal.addInstallment

import com.neoutils.finsight.core.domain.model.Category
import com.neoutils.finsight.core.domain.model.CreditCard
import com.neoutils.finsight.feature.creditCards.model.InvoiceMonthSelection

data class AddInstallmentUiState(
    val categories: List<Category> = emptyList(),
    val creditCards: List<CreditCard> = emptyList(),
    val selectedCreditCard: CreditCard? = null,
    val invoiceSelection: InvoiceMonthSelection? = null,
) {
    val isInvoiceBlocked = invoiceSelection?.isBlocked == true
}
