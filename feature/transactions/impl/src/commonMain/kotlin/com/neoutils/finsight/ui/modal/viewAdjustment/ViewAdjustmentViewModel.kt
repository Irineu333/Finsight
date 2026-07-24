@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.viewAdjustment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.exception.DetailNotFoundException
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.extension.interceptAbsence
import com.neoutils.finsight.ui.model.TransactionFacadeResolver
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

class ViewAdjustmentViewModel(
    transactionId: Long,
    transactionRepository: ITransactionRepository,
    private val facadeResolver: TransactionFacadeResolver,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val _events = Channel<ViewAdjustmentEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState = transactionRepository.observeTransactionById(transactionId)
        .interceptAbsence(
            onMissing = { crashlytics.recordException(DetailNotFoundException("Transaction", transactionId)) },
            onDisappeared = { _events.send(ViewAdjustmentEvent.Dismiss) },
        )
        .mapLatest { transaction ->
            transaction ?: return@mapLatest ViewAdjustmentUiState.Error
            // The card and its invoice are reached through the ledger's identities;
            // resolving them is this feature's job, not the ledger's (design D6).
            val facades = facadeResolver.resolve(transaction)
            ViewAdjustmentUiState.Content(
                transaction = transaction,
                creditCard = facades.creditCard,
                invoice = facades.invoice,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ViewAdjustmentUiState.Loading,
        )
}
