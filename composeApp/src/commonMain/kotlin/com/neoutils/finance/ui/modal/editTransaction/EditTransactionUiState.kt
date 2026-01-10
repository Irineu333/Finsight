package com.neoutils.finance.ui.modal.editTransaction

import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.model.Transaction

data class EditTransactionUiState(
    val incomeCategories: List<Category> = emptyList(),
    val expenseCategories: List<Category> = emptyList(),
    val creditCards: List<CreditCard> = emptyList(),
    val selectedCreditCard: CreditCard? = null,
    val currentInvoice: Invoice? = null
) {
    val targets = if (creditCards.isEmpty()) {
        listOf(Transaction.Target.ACCOUNT)
    } else {
        listOf(Transaction.Target.ACCOUNT, Transaction.Target.CREDIT_CARD)
    }
}
