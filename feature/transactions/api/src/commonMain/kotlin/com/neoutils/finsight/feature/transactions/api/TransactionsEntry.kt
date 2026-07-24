package com.neoutils.finsight.feature.transactions.api

import com.neoutils.finsight.ui.component.AdaptiveModal
import com.neoutils.finsight.ui.component.Modal
import com.neoutils.finsight.ui.model.TransactionPerspective

interface TransactionsEntry {
    fun addTransactionModal(): Modal
    fun viewTransactionModal(transactionId: Long, perspective: TransactionPerspective? = null): AdaptiveModal
    fun viewAdjustmentModal(transactionId: Long): AdaptiveModal
}
