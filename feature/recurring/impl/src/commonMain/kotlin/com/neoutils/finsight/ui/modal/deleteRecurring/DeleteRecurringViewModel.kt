package com.neoutils.finsight.ui.modal.deleteRecurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.DeleteRecurring
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.usecase.DeleteRecurringUseCase
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteRecurringViewModel(
    private val recurring: Recurring,
    private val deleteRecurringUseCase: DeleteRecurringUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    fun delete() = viewModelScope.launch {
        deleteRecurringUseCase(recurring).onRight {
            analytics.logEvent(DeleteRecurring(recurring))
            modalManager.dismissAll()
        }.onLeft {
            crashlytics.recordException(it)
        }
    }
}
