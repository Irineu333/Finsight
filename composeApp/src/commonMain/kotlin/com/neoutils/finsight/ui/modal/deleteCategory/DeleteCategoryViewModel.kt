package com.neoutils.finsight.ui.modal.deleteCategory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteCategoryViewModel(
    private val category: Category,
    private val repository: ICategoryRepository,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
) : ViewModel() {

    fun deleteCategory() = viewModelScope.launch {
        repository.delete(category)
        analytics.logEvent(
            name = "delete_category",
            params = mapOf(
                "name" to category.name,
                "type" to category.type.name.lowercase(),
            )
        )
        modalManager.dismissAll()
    }
}
