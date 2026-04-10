package com.neoutils.finsight.ui.modal.reactivateRecurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
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
            analytics.logEvent(
                name = "reactivate_recurring",
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
