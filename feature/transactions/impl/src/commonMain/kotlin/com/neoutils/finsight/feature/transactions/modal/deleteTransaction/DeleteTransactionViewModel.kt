package com.neoutils.finsight.feature.transactions.modal.deleteTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.core.analytics.Analytics
import com.neoutils.finsight.feature.transactions.event.DeleteTransaction
import com.neoutils.finsight.feature.transactions.model.Transaction
import com.neoutils.finsight.feature.transactions.repository.IOperationRepository
import com.neoutils.finsight.core.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteTransactionViewModel(
    private val transaction: Transaction,
    private val operationRepository: IOperationRepository,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
) : ViewModel() {

    fun deleteTransaction() = viewModelScope.launch {
        operationRepository.deleteOperationById(transaction.operationId ?: transaction.id)
        analytics.logEvent(DeleteTransaction(transaction))
        modalManager.dismissAll()
    }
}
