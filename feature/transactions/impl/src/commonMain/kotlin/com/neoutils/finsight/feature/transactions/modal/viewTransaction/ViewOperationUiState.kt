package com.neoutils.finsight.feature.transactions.modal.viewTransaction

import com.neoutils.finsight.feature.accounts.model.Account
import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.creditCards.model.Invoice
import com.neoutils.finsight.feature.transactions.model.Operation
import com.neoutils.finsight.feature.transactions.model.Transaction

sealed class ViewOperationUiState {

    data object Loading : ViewOperationUiState()

    data object Error : ViewOperationUiState()

    data class Content(
        val operation: Operation,
        val transaction: Transaction,
        val category: Category? = null,
        val account: Account? = null,
        val creditCard: CreditCard? = null,
        val invoice: Invoice? = null,
        val sourceAccount: Account? = null,
        val destinationAccount: Account? = null,
    ) : ViewOperationUiState()
}
