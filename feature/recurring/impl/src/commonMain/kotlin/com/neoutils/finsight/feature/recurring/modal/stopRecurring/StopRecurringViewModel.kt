package com.neoutils.finsight.feature.recurring.modal.stopRecurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.core.analytics.Analytics
import com.neoutils.finsight.feature.recurring.event.StopRecurring
import com.neoutils.finsight.core.analytics.crashlytics.Crashlytics
import com.neoutils.finsight.core.domain.model.Recurring
import com.neoutils.finsight.feature.recurring.usecase.StopRecurringUseCase
import com.neoutils.finsight.core.ui.component.ModalManager
import kotlinx.coroutines.launch

class StopRecurringViewModel(
    private val recurring: Recurring,
    private val stopRecurringUseCase: StopRecurringUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    fun stop() = viewModelScope.launch {
        stopRecurringUseCase(recurring).onLeft {
            crashlytics.recordException(it)
        }.onRight {
            analytics.logEvent(StopRecurring(recurring))
            modalManager.dismissAll()
        }
    }
}
