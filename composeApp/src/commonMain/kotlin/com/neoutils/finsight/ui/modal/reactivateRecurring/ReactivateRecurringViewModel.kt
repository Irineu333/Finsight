package com.neoutils.finsight.ui.modal.reactivateRecurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.ReactivateRecurring
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.usecase.ReactivateRecurringUseCase
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class ReactivateRecurringViewModel(
    private val recurring: Recurring,
    private val reactivateRecurringUseCase: ReactivateRecurringUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
) : ViewModel() {

    fun reactivate() = viewModelScope.launch {
        reactivateRecurringUseCase(recurring).onRight {
            analytics.logEvent(ReactivateRecurring(recurring))
            modalManager.dismissAll()
        }
    }
}
