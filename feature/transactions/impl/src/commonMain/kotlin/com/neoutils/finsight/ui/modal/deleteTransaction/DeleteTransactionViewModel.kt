package com.neoutils.finsight.ui.modal.deleteTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.DeleteTransaction
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.ui.component.ModalManager
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
