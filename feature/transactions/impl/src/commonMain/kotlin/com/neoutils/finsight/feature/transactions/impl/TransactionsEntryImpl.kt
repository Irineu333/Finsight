package com.neoutils.finsight.feature.transactions.impl

import com.neoutils.finsight.feature.transactions.api.TransactionOrigin
import com.neoutils.finsight.feature.transactions.api.TransactionsEntry
import com.neoutils.finsight.ui.component.AdaptiveModal
import com.neoutils.finsight.ui.component.Modal
import com.neoutils.finsight.ui.modal.addTransaction.AddTransactionModal
import com.neoutils.finsight.ui.modal.viewTransaction.ViewTransactionModal

internal class TransactionsEntryImpl : TransactionsEntry {
    override fun addTransactionModal(origin: TransactionOrigin?): Modal =
        AddTransactionModal(origin)
    override fun viewTransactionModal(transactionId: Long): AdaptiveModal =
        ViewTransactionModal(transactionId)
}
