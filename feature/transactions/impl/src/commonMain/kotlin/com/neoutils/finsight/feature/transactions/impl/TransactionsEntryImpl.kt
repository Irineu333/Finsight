package com.neoutils.finsight.feature.transactions.impl

import com.neoutils.finsight.feature.transactions.api.TransactionsEntry
import com.neoutils.finsight.ui.component.AdaptiveModal
import com.neoutils.finsight.ui.component.Modal
import com.neoutils.finsight.ui.model.OperationPerspective
import com.neoutils.finsight.ui.modal.addTransaction.AddTransactionModal
import com.neoutils.finsight.ui.modal.viewAdjustment.ViewAdjustmentModal
import com.neoutils.finsight.ui.modal.viewTransaction.ViewOperationModal

internal class TransactionsEntryImpl : TransactionsEntry {
    override fun addTransactionModal(): Modal = AddTransactionModal()
    override fun viewOperationModal(operationId: Long, perspective: OperationPerspective?): AdaptiveModal =
        ViewOperationModal(operationId, perspective)
    override fun viewAdjustmentModal(operationId: Long): AdaptiveModal = ViewAdjustmentModal(operationId)
}
