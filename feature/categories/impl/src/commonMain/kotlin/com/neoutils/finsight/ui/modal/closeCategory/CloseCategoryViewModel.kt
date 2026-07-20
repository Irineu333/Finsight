package com.neoutils.finsight.ui.modal.closeCategory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.DeleteCategory
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.usecase.CloseCategoryUseCase
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class CloseCategoryViewModel(
    private val category: Category,
    private val closeCategoryUseCase: CloseCategoryUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    fun closeCategory() = viewModelScope.launch {
        closeCategoryUseCase(category).onRight {
            analytics.logEvent(DeleteCategory(category))
            modalManager.dismissAll()
        }.onLeft {
            crashlytics.recordException(it)
        }
    }
}
