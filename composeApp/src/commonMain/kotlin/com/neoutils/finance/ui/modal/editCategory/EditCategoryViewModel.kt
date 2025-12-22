package com.neoutils.finance.ui.modal.editCategory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EditCategoryViewModel(
    private val category: Category,
    private val repository: ICategoryRepository,
    private val modalManager: ModalManager
) : ViewModel() {

    val existingCategories = repository
        .observeCategoriesByType(category.type)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateCategory(updatedCategory: Category) = viewModelScope.launch {
        repository.update(updatedCategory)
        modalManager.dismiss()
    }
}
