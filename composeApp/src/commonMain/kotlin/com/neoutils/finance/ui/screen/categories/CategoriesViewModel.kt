package com.neoutils.finance.ui.screen.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.data.Category
import com.neoutils.finance.data.CategoryRepository
import com.neoutils.finance.usecase.GetCategoriesUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriesViewModel(
    private val getCategoriesUseCase: GetCategoriesUseCase
) : ViewModel() {

    val uiState: StateFlow<CategoriesUiState> = getCategoriesUseCase()
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
