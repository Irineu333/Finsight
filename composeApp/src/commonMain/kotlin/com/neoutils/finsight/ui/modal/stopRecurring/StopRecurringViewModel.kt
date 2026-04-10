package com.neoutils.finsight.ui.modal.stopRecurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.StopRecurring
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.usecase.StopRecurringUseCase
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class StopRecurringViewModel(
    private val recurring: Recurring,
    private val stopRecurringUseCase: StopRecurringUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
) : ViewModel() {

    fun stop() = viewModelScope.launch {
        stopRecurringUseCase(recurring).onRight {
            analytics.logEvent(StopRecurring(recurring))
            modalManager.dismissAll()
        }
    }
}
