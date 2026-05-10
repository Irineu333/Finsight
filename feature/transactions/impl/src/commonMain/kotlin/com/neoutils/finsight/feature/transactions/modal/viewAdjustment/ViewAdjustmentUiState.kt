package com.neoutils.finsight.feature.transactions.modal.viewAdjustment

import com.neoutils.finsight.feature.accounts.model.Account
import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.creditCards.model.Invoice
import com.neoutils.finsight.feature.transactions.model.Operation
import com.neoutils.finsight.feature.transactions.model.Transaction

sealed class ViewAdjustmentUiState {

    data object Loading : ViewAdjustmentUiState()

    data object Error : ViewAdjustmentUiState()

    data class Content(
        val operation: Operation,
        val account: Account? = null,
        val creditCard: CreditCard? = null,
        val invoice: Invoice? = null,
    ) : ViewAdjustmentUiState() {
        val transaction: Transaction = operation.primaryTransaction
    }
}
