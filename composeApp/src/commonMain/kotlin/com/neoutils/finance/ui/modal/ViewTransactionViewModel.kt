@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ITransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.time.ExperimentalTime
import com.neoutils.finance.domain.model.Category

class ViewTransactionViewModel(
    private val transaction: Transaction,
    private val transactionRepository: ITransactionRepository,
    private val categoryRepository: ICategoryRepository
) : ViewModel() {

    val uiState: StateFlow<ViewTransactionUiState> = combine(
        transactionRepository.observeTransactionById(transaction.id),
        categoryRepository.getAllCategories()
    ) { observedTransaction, categories ->
        val currentTransaction = observedTransaction ?: transaction
        val category = currentTransaction.categoryId?.let { categoryId ->
            categories.find { it.id == categoryId }
        }

        ViewTransactionUiState(
            transaction = currentTransaction,
            category = category
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ViewTransactionUiState(transaction = transaction)
    )
}

data class ViewTransactionUiState(
    val transaction: Transaction,
    val category: Category? = null
)
