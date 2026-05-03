package com.neoutils.finsight.feature.recurring.modal.deleteRecurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.core.analytics.Analytics
import com.neoutils.finsight.feature.recurring.event.DeleteRecurring
import com.neoutils.finsight.core.domain.model.Recurring
import com.neoutils.finsight.feature.recurring.repository.IRecurringRepository
import com.neoutils.finsight.core.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteRecurringViewModel(
    private val recurring: Recurring,
    private val recurringRepository: IRecurringRepository,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
) : ViewModel() {

    fun delete() = viewModelScope.launch {
        recurringRepository.delete(recurring)
        analytics.logEvent(DeleteRecurring(recurring))
        modalManager.dismissAll()
    }
}
