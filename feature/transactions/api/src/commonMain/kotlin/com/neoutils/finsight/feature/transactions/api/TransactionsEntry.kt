package com.neoutils.finsight.feature.transactions.api

import com.neoutils.finsight.ui.component.AdaptiveModal
import com.neoutils.finsight.ui.component.Modal

interface TransactionsEntry {
    fun addTransactionModal(): Modal
    fun viewTransactionModal(transactionId: Long): AdaptiveModal
    fun viewAdjustmentModal(transactionId: Long): AdaptiveModal
}
