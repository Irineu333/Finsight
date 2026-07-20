package com.neoutils.finsight.ui.modal.editTransaction

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.InvoiceMonthSelection
import com.neoutils.finsight.domain.model.TransactionTarget

data class EditTransactionUiState(
    val incomeCategories: List<Category> = emptyList(),
    val expenseCategories: List<Category> = emptyList(),
    val creditCards: List<CreditCard> = emptyList(),
    val selectedCreditCard: CreditCard? = null,
    val invoiceSelection: InvoiceMonthSelection? = null,
    val accounts: List<Account> = emptyList(),
    val selectedAccount: Account? = null,
) {
    val targets = listOf(TransactionTarget.ACCOUNT, TransactionTarget.CREDIT_CARD)

    val isInvoiceBlocked = invoiceSelection?.isClosedToNewExpenses == true
}
