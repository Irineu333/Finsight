package com.neoutils.finsight.feature.transactions.modal.editTransaction

import com.neoutils.finsight.feature.accounts.model.Account
import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.creditCards.model.InvoiceMonthSelection
import com.neoutils.finsight.feature.transactions.model.Transaction

data class EditTransactionUiState(
    val incomeCategories: List<Category> = emptyList(),
    val expenseCategories: List<Category> = emptyList(),
    val creditCards: List<CreditCard> = emptyList(),
    val selectedCreditCard: CreditCard? = null,
    val invoiceSelection: InvoiceMonthSelection? = null,
    val accounts: List<Account> = emptyList(),
    val selectedAccount: Account? = null,
) {
    val targets = listOf(Transaction.Target.ACCOUNT, Transaction.Target.CREDIT_CARD)

    val isInvoiceBlocked = invoiceSelection?.isBlocked == true
}
