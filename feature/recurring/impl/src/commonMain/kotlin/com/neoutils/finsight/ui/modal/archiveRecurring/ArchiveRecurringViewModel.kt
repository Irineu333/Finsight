package com.neoutils.finsight.ui.modal.archiveRecurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.ArchiveRecurring
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.error.toRecurringRetireUiMessage
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.usecase.ArchiveRecurringUseCase
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class ArchiveRecurringViewModel(
    private val recurring: Recurring,
    private val archiveRecurringUseCase: ArchiveRecurringUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    fun archive() = viewModelScope.launch {
        archiveRecurringUseCase(recurring).onLeft {
            crashlytics.recordException(it)
            modalManager.showError(it.toRecurringRetireUiMessage())
        }.onRight {
            analytics.logEvent(ArchiveRecurring(recurring))
            modalManager.dismissAll()
        }
    }
}
