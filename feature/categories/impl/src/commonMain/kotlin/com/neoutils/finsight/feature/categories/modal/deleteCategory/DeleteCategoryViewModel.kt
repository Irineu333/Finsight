package com.neoutils.finsight.feature.categories.modal.deleteCategory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.core.analytics.Analytics
import com.neoutils.finsight.feature.categories.event.DeleteCategory
import com.neoutils.finsight.core.domain.model.Category
import com.neoutils.finsight.feature.categories.repository.ICategoryRepository
import com.neoutils.finsight.core.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteCategoryViewModel(
    private val category: Category,
    private val repository: ICategoryRepository,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
) : ViewModel() {

    fun deleteCategory() = viewModelScope.launch {
        repository.delete(category)
        analytics.logEvent(DeleteCategory(category))
        modalManager.dismissAll()
    }
}
