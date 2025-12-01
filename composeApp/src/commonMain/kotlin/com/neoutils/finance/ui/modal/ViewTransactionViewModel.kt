@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.data.CategoryRepository
import com.neoutils.finance.data.TransactionEntry
import com.neoutils.finance.data.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.time.ExperimentalTime
import com.neoutils.finance.data.Category

class ViewTransactionViewModel(
    private val transaction: TransactionEntry,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
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
    val transaction: TransactionEntry,
    val category: Category? = null
)
