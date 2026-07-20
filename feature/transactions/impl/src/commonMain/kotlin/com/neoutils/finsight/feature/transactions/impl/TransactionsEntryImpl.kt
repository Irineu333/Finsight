package com.neoutils.finsight.feature.transactions.impl

import com.neoutils.finsight.feature.transactions.api.TransactionsEntry
import com.neoutils.finsight.ui.component.AdaptiveModal
import com.neoutils.finsight.ui.component.Modal
import com.neoutils.finsight.ui.model.TransactionPerspective
import com.neoutils.finsight.ui.modal.addTransaction.AddTransactionModal
import com.neoutils.finsight.ui.modal.viewAdjustment.ViewAdjustmentModal
import com.neoutils.finsight.ui.modal.viewTransaction.ViewOperationModal

internal class TransactionsEntryImpl : TransactionsEntry {
    override fun addTransactionModal(): Modal = AddTransactionModal()
    override fun viewOperationModal(transactionId: Long, perspective: TransactionPerspective?): AdaptiveModal =
        ViewOperationModal(transactionId, perspective)
    override fun viewAdjustmentModal(transactionId: Long): AdaptiveModal = ViewAdjustmentModal(transactionId)
}
