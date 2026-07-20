package com.neoutils.finsight.ui.modal.viewAdjustment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.exception.DetailNotFoundException
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.extension.interceptAbsence
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

class ViewAdjustmentViewModel(
    transactionId: Long,
    transactionRepository: ITransactionRepository,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val _events = Channel<ViewAdjustmentEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState = transactionRepository.observeTransactionById(transactionId)
        .interceptAbsence(
            onMissing = { crashlytics.recordException(DetailNotFoundException("Transaction", transactionId)) },
            onDisappeared = { _events.send(ViewAdjustmentEvent.Dismiss) },
        )
        .map { transaction ->
            transaction?.let { ViewAdjustmentUiState.Content(it) }
                ?: ViewAdjustmentUiState.Error
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ViewAdjustmentUiState.Loading,
        )
}
