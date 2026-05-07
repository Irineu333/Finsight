package com.neoutils.finsight.feature.recurring.modal.recurringForm

import com.neoutils.finsight.feature.accounts.model.Account
import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.recurring.model.form.RecurringForm
import com.neoutils.finsight.feature.transactions.model.Transaction

sealed class RecurringFormUiState {

    data object Loading : RecurringFormUiState()

    data class Content(
        val form: RecurringForm,
        val accounts: List<Account>,
        val creditCards: List<CreditCard>,
        val incomeCategories: List<Category>,
        val expenseCategories: List<Category>,
        val isEditMode: Boolean,
    ) : RecurringFormUiState() {
        val targets = listOf(Transaction.Target.ACCOUNT, Transaction.Target.CREDIT_CARD)
    }
}
