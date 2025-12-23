package com.neoutils.finance.ui.modal.viewAdjustment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ITransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ViewAdjustmentViewModel(
    transaction: Transaction,
    transactionRepository: ITransactionRepository,
) : ViewModel() {

    private val transactionFlow = transactionRepository
        .observeTransactionById(transaction.id)
        .filterNotNull()

    val uiState = transactionFlow
        .map { ViewAdjustmentUiState(transaction = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ViewAdjustmentUiState(transaction = transaction)
        )
}

