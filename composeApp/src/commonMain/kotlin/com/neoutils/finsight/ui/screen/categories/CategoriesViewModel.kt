package com.neoutils.finsight.ui.screen.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.usecase.CreateDefaultCategoriesUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriesViewModel(
    private val categoryRepository: ICategoryRepository,
    private val createDefaultCategories: CreateDefaultCategoriesUseCase,
) : ViewModel() {

    val uiState = categoryRepository
        .observeAllCategories()
        .map { categories ->
            CategoriesUiState(
                categories = categories.sortedBy { it.name },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CategoriesUiState()
        )

    fun onAction(action: CategoriesAction) {
        when (action) {
            CategoriesAction.CreateDefaultCategories -> viewModelScope.launch {
                createDefaultCategories()
            }
        }
    }
}
