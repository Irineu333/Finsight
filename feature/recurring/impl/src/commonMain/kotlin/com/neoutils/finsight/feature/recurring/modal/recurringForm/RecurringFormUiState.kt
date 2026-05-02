package com.neoutils.finsight.feature.recurring.modal.recurringForm

import com.neoutils.finsight.core.domain.model.Account
import com.neoutils.finsight.core.domain.model.Category
import com.neoutils.finsight.core.domain.model.CreditCard
import com.neoutils.finsight.feature.transactions.model.Transaction
data class RecurringFormUiState(
    val accounts: List<Account> = emptyList(),
    val selectedAccount: Account? = null,
    val creditCards: List<CreditCard> = emptyList(),
    val selectedCreditCard: CreditCard? = null,
    val incomeCategories: List<Category> = emptyList(),
    val expenseCategories: List<Category> = emptyList(),
) {
    val targets = listOf(Transaction.Target.ACCOUNT, Transaction.Target.CREDIT_CARD)
}
