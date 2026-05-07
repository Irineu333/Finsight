package com.neoutils.finsight.feature.recurring.modal.viewRecurring

import com.neoutils.finsight.feature.accounts.model.Account
import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.recurring.model.Recurring

sealed class ViewRecurringUiState {
    data object Loading : ViewRecurringUiState()
    data object Error : ViewRecurringUiState()
    data class Content(
        val recurring: Recurring,
        val account: Account?,
        val category: Category?,
        val creditCard: CreditCard?,
    ) : ViewRecurringUiState()
}
