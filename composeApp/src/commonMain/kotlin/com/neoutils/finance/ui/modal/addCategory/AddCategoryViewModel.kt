package com.neoutils.finance.ui.modal.addCategory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AddCategoryViewModel(
    private val initialType: Category.Type,
    private val repository: ICategoryRepository,
    private val modalManager: ModalManager
) : ViewModel() {

    val categories = repository
        .observeCategoriesByType(initialType)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addCategory(category: Category) = viewModelScope.launch {
        repository.insert(category)
        modalManager.dismiss()
    }
}
