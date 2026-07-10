package com.neoutils.finsight.feature.transactions.impl

import androidx.navigation.NavGraphBuilder
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.feature.transactions.api.TransactionsEntry
import com.neoutils.finsight.ui.component.Modal
import com.neoutils.finsight.ui.model.OperationPerspective
import com.neoutils.finsight.ui.model.OperationUi
import com.neoutils.finsight.ui.modal.addTransaction.AddTransactionModal
import com.neoutils.finsight.ui.modal.viewAdjustment.ViewAdjustmentModal
import com.neoutils.finsight.ui.modal.viewTransaction.ViewOperationModal
import com.neoutils.finsight.ui.navigation.transactionsGraph

internal class TransactionsEntryImpl : TransactionsEntry {
    context(builder: NavGraphBuilder)
    override fun register() = builder.transactionsGraph()

    override fun addTransactionModal(): Modal = AddTransactionModal()
    override fun viewOperationModal(operationUi: OperationUi): Modal = ViewOperationModal(operationUi)
    override fun viewOperationModal(operation: Operation, perspective: OperationPerspective?): Modal =
        ViewOperationModal(operation, perspective)
    override fun viewAdjustmentModal(operation: Operation): Modal = ViewAdjustmentModal(operation)
}
