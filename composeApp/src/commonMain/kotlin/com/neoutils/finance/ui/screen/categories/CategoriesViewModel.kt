package com.neoutils.finance.ui.screen.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.repository.ICategoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class CategoriesViewModel(
    private val repository: ICategoryRepository,
) : ViewModel() {

    val uiState = repository.observeAllCategories()
        .map { categories ->
            CategoriesUiState(
                categories = categories.sortedBy { it.name }
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CategoriesUiState()
        )
}
