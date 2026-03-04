package com.neoutils.finsight.ui.modal.deleteRecurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteRecurringViewModel(
    private val recurring: Recurring,
    private val recurringRepository: IRecurringRepository,
    private val modalManager: ModalManager,
) : ViewModel() {

    fun delete() = viewModelScope.launch {
        recurringRepository.delete(recurring)
        modalManager.dismissAll()
    }
}
