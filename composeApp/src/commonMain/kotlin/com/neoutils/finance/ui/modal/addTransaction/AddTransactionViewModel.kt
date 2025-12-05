package com.neoutils.finance.ui.modal.addTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AddTransactionViewModel(
    private val transactionRepository: ITransactionRepository,
    private val categoryRepository: ICategoryRepository,
    private val modalManager: ModalManager
) : ViewModel() {

    val uiState = categoryRepository
        .getAllCategories()
        .map { categories ->
            AddTransactionUiState(
                incomeCategories = categories.filter { it.type.isIncome },
                expenseCategories = categories.filter { it.type.isExpense },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AddTransactionUiState(),
        )

    fun addTransaction(
        transaction: Transaction
    ) = viewModelScope.launch {
        transactionRepository.insert(transaction)
        modalManager.dismiss()
    }
}
