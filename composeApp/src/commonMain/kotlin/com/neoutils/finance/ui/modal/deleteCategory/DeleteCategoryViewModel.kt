package com.neoutils.finance.ui.modal.deleteCategory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteCategoryViewModel(
    private val category: Category,
    private val repository: ICategoryRepository,
    private val modalManager: ModalManager
) : ViewModel() {

    fun deleteCategory() = viewModelScope.launch {
        repository.delete(category)
        modalManager.dismissAll()
    }
}
