package com.neoutils.finance.ui.modal.editTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EditTransactionUiState(
    val incomeCategories: List<Category> = emptyList(),
    val expenseCategories: List<Category> = emptyList(),
)

class EditTransactionViewModel(
    private val transactionRepository: ITransactionRepository,
    private val categoryRepository: ICategoryRepository,
    private val modalManager: ModalManager
) : ViewModel() {

    val uiState = categoryRepository
        .getAllCategories()
        .map { categories ->
            EditTransactionUiState(
                incomeCategories = categories.filter { it.type == Category.Type.INCOME },
                expenseCategories = categories.filter { it.type == Category.Type.EXPENSE },
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = EditTransactionUiState()
        )

    fun updateTransaction(
        transaction: Transaction
    ) = viewModelScope.launch {
        transactionRepository.update(transaction)
        modalManager.dismiss()
    }
}
