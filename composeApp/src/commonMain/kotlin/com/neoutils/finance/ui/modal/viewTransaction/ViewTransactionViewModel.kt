package com.neoutils.finance.ui.modal.viewTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ITransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ViewTransactionViewModel(
    transaction: Transaction,
    transactionRepository: ITransactionRepository,
) : ViewModel() {

    val uiState = transactionRepository
        .observeTransactionById(transaction.id)
        .filterNotNull()
        .map { ViewTransactionUiState(transaction = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ViewTransactionUiState(transaction = transaction)
        )
}
