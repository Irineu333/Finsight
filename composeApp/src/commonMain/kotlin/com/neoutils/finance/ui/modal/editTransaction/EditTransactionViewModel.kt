package com.neoutils.finance.ui.modal.editTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.launch

class EditTransactionViewModel(
    private val transaction: Transaction,
    private val repository: ITransactionRepository,
    private val modalManager: ModalManager
) : ViewModel() {

    fun updateTransaction(updatedTransaction: Transaction) = viewModelScope.launch {
        repository.update(updatedTransaction)
        modalManager.dismiss()
    }
}
