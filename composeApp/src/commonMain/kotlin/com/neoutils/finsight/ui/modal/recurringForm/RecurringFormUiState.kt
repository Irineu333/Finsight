package com.neoutils.finsight.ui.modal.recurringForm

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Transaction

data class RecurringFormUiState(
    val accounts: List<Account> = emptyList(),
    val selectedAccount: Account? = null,
    val creditCards: List<CreditCard> = emptyList(),
    val selectedCreditCard: CreditCard? = null,
    val incomeCategories: List<Category> = emptyList(),
    val expenseCategories: List<Category> = emptyList(),
) {
    val targets: List<Transaction.Target>
        get() = if (creditCards.isEmpty()) {
            listOf(Transaction.Target.ACCOUNT)
        } else {
            listOf(Transaction.Target.ACCOUNT, Transaction.Target.CREDIT_CARD)
        }
}
