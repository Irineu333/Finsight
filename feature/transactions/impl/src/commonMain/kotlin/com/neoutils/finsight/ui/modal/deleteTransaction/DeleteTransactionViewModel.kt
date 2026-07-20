package com.neoutils.finsight.ui.modal.deleteTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.DeleteTransaction
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteTransactionViewModel(
    private val operation: Operation,
    private val operationRepository: IOperationRepository,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
) : ViewModel() {

    fun deleteTransaction() = viewModelScope.launch {
        operationRepository.deleteOperationById(operation.id)
        analytics.logEvent(DeleteTransaction(operation))
        modalManager.dismissAll()
    }
}
