package com.neoutils.finsight.feature.transactions.modal.viewAdjustment

import com.neoutils.finsight.core.domain.model.Account
import com.neoutils.finsight.core.domain.model.CreditCard
import com.neoutils.finsight.core.domain.model.Invoice
import com.neoutils.finsight.core.domain.model.Operation
import com.neoutils.finsight.core.domain.model.Transaction

data class ViewAdjustmentUiState(
    val operation: Operation,
    val account: Account? = null,
    val creditCard: CreditCard? = null,
    val invoice: Invoice? = null,
) {
    val transaction: Transaction = operation.primaryTransaction
}
