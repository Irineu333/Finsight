package com.neoutils.finsight.feature.transactions.modal.viewAdjustment

import com.neoutils.finsight.feature.accounts.model.Account
import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.creditCards.model.Invoice
import com.neoutils.finsight.feature.transactions.model.Operation
import com.neoutils.finsight.feature.transactions.model.Transaction

data class ViewAdjustmentUiState(
    val operation: Operation,
    val account: Account? = null,
    val creditCard: CreditCard? = null,
    val invoice: Invoice? = null,
) {
    val transaction: Transaction = operation.primaryTransaction
}
