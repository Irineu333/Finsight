package com.neoutils.finance.ui.modal.deleteTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteTransactionViewModel(
    private val transaction: Transaction,
    private val repository: ITransactionRepository,
    private val modalManager: ModalManager
) : ViewModel() {

    fun deleteTransaction() = viewModelScope.launch {
        repository.delete(transaction)
        modalManager.dismissAll()
    }
}
