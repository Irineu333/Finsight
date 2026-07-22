@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.viewTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.exception.DetailNotFoundException
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.extension.interceptAbsence
import com.neoutils.finsight.ui.model.TransactionFacadeResolver
import com.neoutils.finsight.ui.model.TransactionPerspective
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ViewTransactionViewModel(
    transactionId: Long,
    private val perspective: TransactionPerspective? = null,
    transactionRepository: ITransactionRepository,
    private val facadeResolver: TransactionFacadeResolver,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val _events = Channel<ViewTransactionEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState = transactionRepository.observeTransactionById(transactionId)
        .interceptAbsence(
            onMissing = { crashlytics.recordException(DetailNotFoundException("Transaction", transactionId)) },
            onDisappeared = { _events.send(ViewTransactionEvent.Dismiss) },
        )
        .mapLatest { transaction ->
            transaction ?: return@mapLatest ViewTransactionUiState.Error
            // The ledger says which account and dimension; the facades behind them
            // are resolved here, where this feature is allowed to know them.
            val facades = facadeResolver.resolve(transaction)
            ViewTransactionUiState.Content(
                transaction = transaction,
                perspective = perspective,
                category = facades.category,
                creditCard = facades.creditCard,
                invoice = facades.invoice,
                installment = facades.installment,
                recurring = facades.recurring,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ViewTransactionUiState.Loading,
        )

    fun onAction(action: ViewTransactionAction) = viewModelScope.launch {
        when (action) {
            is ViewTransactionAction.OpenRecurring -> {
                _events.send(
                    ViewTransactionEvent.OpenRecurring(action.recurring)
                )
            }
        }
    }
}
