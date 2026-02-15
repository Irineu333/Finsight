package com.neoutils.finance.ui.modal.deleteTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.IOperationRepository
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteTransactionViewModel(
    private val transaction: Transaction,
    private val operationRepository: IOperationRepository,
    private val modalManager: ModalManager
) : ViewModel() {

    fun deleteTransaction() = viewModelScope.launch {
        operationRepository.deleteOperationById(transaction.operationId ?: transaction.id)
        modalManager.dismissAll()
    }
}
