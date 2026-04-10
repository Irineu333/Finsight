package com.neoutils.finsight.ui.modal.deleteTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
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
        analytics.logEvent(
            name = "delete_transaction",
            params = buildMap {
                put("type", transaction.type.name.lowercase())
                put("target", transaction.target.name.lowercase())
                transaction.category?.let { put("category", it.name) }
            }
        )
        modalManager.dismissAll()
    }
}
