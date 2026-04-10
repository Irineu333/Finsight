package com.neoutils.finsight.ui.modal.stopRecurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
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
            analytics.logEvent(
                name = "stop_recurring",
                params = buildMap {
                    put("type", recurring.type.name.lowercase())
                    put("target", if (recurring.creditCard != null) "credit_card" else "account")
                    recurring.category?.let { put("category", it.name) }
                }
            )
            modalManager.dismissAll()
        }
    }
}
