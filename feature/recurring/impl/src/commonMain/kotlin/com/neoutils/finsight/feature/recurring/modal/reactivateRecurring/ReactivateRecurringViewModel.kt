package com.neoutils.finsight.feature.recurring.modal.reactivateRecurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.core.analytics.Analytics
import com.neoutils.finsight.feature.recurring.event.ReactivateRecurring
import com.neoutils.finsight.core.analytics.crashlytics.Crashlytics
import com.neoutils.finsight.core.domain.model.Recurring
import com.neoutils.finsight.feature.recurring.usecase.ReactivateRecurringUseCase
import com.neoutils.finsight.core.ui.component.ModalManager
import kotlinx.coroutines.launch

class ReactivateRecurringViewModel(
    private val recurring: Recurring,
    private val reactivateRecurringUseCase: ReactivateRecurringUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    fun reactivate() = viewModelScope.launch {
        reactivateRecurringUseCase(recurring).onLeft {
            crashlytics.recordException(it)
        }.onRight {
            analytics.logEvent(ReactivateRecurring(recurring))
            modalManager.dismissAll()
        }
    }
}
