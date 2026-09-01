package com.neoutils.finsight.feature.transactions.api

import com.neoutils.finsight.ui.component.AdaptiveModal
import com.neoutils.finsight.ui.component.Modal

interface TransactionsEntry {
    /**
     * The form for a new transaction, born pointing at [origin] when the caller has something in
     * focus. A caller with no context calls it without one, and the form opens with its own
     * defaults.
     */
    fun addTransactionModal(origin: TransactionOrigin? = null): Modal
    fun viewTransactionModal(transactionId: Long): AdaptiveModal
}
