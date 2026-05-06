package com.neoutils.finsight.feature.transactions.modal.addTransaction

import com.neoutils.finsight.feature.accounts.model.Account
import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.creditCards.model.InvoiceMonth
import com.neoutils.finsight.feature.transactions.model.Transaction

data class AddTransactionUiState(
    val incomeCategories: List<Category> = emptyList(),
    val expenseCategories: List<Category> = emptyList(),
    val creditCards: List<CreditCard> = emptyList(),
    val selectedCreditCard: CreditCard? = null,
    val invoiceSelection: InvoiceMonth? = null,
    val accounts: List<Account> = emptyList(),
    val selectedAccount: Account? = null,
) {
    val targets = listOf(Transaction.Target.ACCOUNT, Transaction.Target.CREDIT_CARD)

    val isInvoiceBlocked = invoiceSelection?.isBlocked == true
}