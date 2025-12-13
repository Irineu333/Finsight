@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal.viewTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlin.time.ExperimentalTime

class ViewTransactionViewModel(
    private val transaction: Transaction,
    private val transactionRepository: ITransactionRepository,
    private val creditCardRepository: ICreditCardRepository
) : ViewModel() {

    private val transactionFlow = transactionRepository
        .observeTransactionById(transaction.id)
        .filterNotNull()

    private val creditCardFlow = transaction.creditCardId?.let {
        creditCardRepository.observeCreditCardById(it)
    } ?: flowOf(null)

    val uiState = combine(transactionFlow, creditCardFlow) { transaction, creditCard ->
        ViewTransactionUiState(
            transaction = transaction,
            creditCardName = creditCard?.name
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ViewTransactionUiState(transaction = transaction)
    )
}
