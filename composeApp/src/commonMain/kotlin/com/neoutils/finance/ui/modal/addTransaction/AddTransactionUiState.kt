package com.neoutils.finance.ui.modal.addTransaction

import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.ui.model.InvoiceUi

data class AddTransactionUiState(
    val incomeCategories: List<Category> = emptyList(),
    val expenseCategories: List<Category> = emptyList(),
    val creditCards: List<CreditCard> = emptyList(),
    val selectedCreditCard: CreditCard? = null,
    val currentInvoiceUi: InvoiceUi? = null
) {
    val currentInvoice get() = currentInvoiceUi?.invoice
    val targets = if (creditCards.isEmpty()) {
        listOf(Transaction.Target.ACCOUNT)
    } else {
        listOf(Transaction.Target.ACCOUNT, Transaction.Target.CREDIT_CARD)
    }
}