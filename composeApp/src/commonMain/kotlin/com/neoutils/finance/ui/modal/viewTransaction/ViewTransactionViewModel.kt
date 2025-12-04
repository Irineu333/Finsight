@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal.viewTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ITransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlin.time.ExperimentalTime

class ViewTransactionViewModel(
    private val transaction: Transaction,
    private val transactionRepository: ITransactionRepository,
    private val categoryRepository: ICategoryRepository
) : ViewModel() {

    private val categoryFlow = flow {
        if (transaction.category != null) {
            emitAll(categoryRepository.observeCategoryById(transaction.category.id))
        } else {
            emit(null)
        }
    }

    private val transactionFlow = transactionRepository
        .observeTransactionById(transaction.id)
        .filterNotNull()

    val uiState = combine(
        transactionFlow,
        categoryFlow,
    ) { transaction, category ->
        ViewTransactionUiState(
            transaction = transaction,
            category = category,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ViewTransactionUiState(
            transaction = transaction
        )
    )
}
