package com.neoutils.finsight.ui.modal.archiveCategory

import com.neoutils.finsight.domain.error.toRetireUiMessage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.DeleteCategory
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.usecase.ArchiveCategoryUseCase
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class ArchiveCategoryViewModel(
    private val category: Category,
    private val archiveCategoryUseCase: ArchiveCategoryUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    fun archiveCategory() = viewModelScope.launch {
        archiveCategoryUseCase(category).onRight {
            analytics.logEvent(DeleteCategory(category))
            modalManager.dismissAll()
        }.onLeft {
            crashlytics.recordException(it)
            modalManager.showError(it.toRetireUiMessage())
        }
    }
}
