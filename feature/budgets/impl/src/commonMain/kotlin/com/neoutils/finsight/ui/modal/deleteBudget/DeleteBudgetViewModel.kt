package com.neoutils.finsight.ui.modal.deleteBudget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.DeleteBudget
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.usecase.DeleteBudgetUseCase
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteBudgetViewModel(
    private val budget: Budget,
    private val deleteBudgetUseCase: DeleteBudgetUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    fun deleteBudget() = viewModelScope.launch {
        deleteBudgetUseCase(budget).onLeft {
            crashlytics.recordException(it)
        }.onRight {
            analytics.logEvent(DeleteBudget)
            modalManager.dismissAll()
        }
    }
}
