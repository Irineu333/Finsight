@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal.viewAdjustment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ICreditCardRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.time.ExperimentalTime

class ViewAdjustmentViewModel(
    private val transaction: Transaction,
    private val creditCardRepository: ICreditCardRepository
) : ViewModel() {

    private val creditCardFlow = transaction.creditCardId?.let {
        creditCardRepository.observeCreditCardById(it)
    } ?: flowOf(null)

    val uiState = creditCardFlow.map { creditCard ->
        ViewAdjustmentUiState(
            transaction = transaction,
            creditCardName = creditCard?.name
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ViewAdjustmentUiState(transaction = transaction)
    )
}

data class ViewAdjustmentUiState(
    val transaction: Transaction,
    val creditCardName: String? = null
)
