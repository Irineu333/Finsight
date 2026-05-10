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

    sealed class Content : ViewOperationUiState() {

        abstract val operation: Operation
        abstract val transaction: Transaction
        abstract val category: Category?

        data class Single(
            override val operation: Operation,
            override val transaction: Transaction,
            override val category: Category? = null,
            val account: Account? = null,
            val creditCard: CreditCard? = null,
            val invoice: Invoice? = null,
        ) : Content()

        data class Transfer(
            override val operation: Operation,
            override val transaction: Transaction,
            override val category: Category? = null,
            val sourceAccount: Account? = null,
            val destinationAccount: Account? = null,
        ) : Content()

        data class Payment(
            override val operation: Operation,
            override val transaction: Transaction,
            override val category: Category? = null,
            val sourceAccount: Account? = null,
            val creditCard: CreditCard? = null,
            val invoice: Invoice? = null,
        ) : Content()
    }
}
