package com.neoutils.finance.ui.modal.addTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.launch

class AddTransactionViewModel(
    private val repository: ITransactionRepository,
    private val modalManager: ModalManager
) : ViewModel() {

    fun addTransaction(
        transaction: Transaction
    ) = viewModelScope.launch {
        repository.insert(transaction)
        modalManager.dismiss()
    }
}
