package com.neoutils.finance.ui.modal.editCategory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.usecase.GetCategoriesUseCase
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EditCategoryViewModel(
    private val category: Category,
    private val repository: ICategoryRepository,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    val existingCategories = getCategoriesUseCase(category.type)
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
