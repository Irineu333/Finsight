package com.neoutils.finsight.ui.modal.stopRecurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.usecase.StopRecurringUseCase
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class StopRecurringViewModel(
    private val recurring: Recurring,
    private val stopRecurringUseCase: StopRecurringUseCase,
    private val modalManager: ModalManager,
) : ViewModel() {

    fun stop() = viewModelScope.launch {
        stopRecurringUseCase(recurring)
            .onRight { modalManager.dismissAll() }
    }
}
