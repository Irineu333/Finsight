package com.neoutils.finsight.ui.modal.unarchiveRecurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.UnarchiveRecurring
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.usecase.UnarchiveRecurringUseCase
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class UnarchiveRecurringViewModel(
    private val recurring: Recurring,
    private val unarchiveRecurringUseCase: UnarchiveRecurringUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    fun unarchive() = viewModelScope.launch {
        unarchiveRecurringUseCase(recurring).onLeft {
            crashlytics.recordException(it)
        }.onRight {
            analytics.logEvent(UnarchiveRecurring(recurring))
            modalManager.dismissAll()
        }
    }
}
