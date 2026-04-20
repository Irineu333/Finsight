package com.neoutils.finsight.ui.screen.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.usecase.CreateDefaultCategoriesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriesViewModel(
    private val categoryRepository: ICategoryRepository,
    private val createDefaultCategories: CreateDefaultCategoriesUseCase,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val selectedType = MutableStateFlow<Category.Type?>(null)

    val uiState = combine(
        categoryRepository.observeAllCategories(),
        selectedType,
    ) { categories, selectedType ->
        val sortedCategories = categories.sortedBy { it.name }
        val availableTypes = sortedCategories.map { it.type }.distinct()
        val resolvedSelectedType = when {
            selectedType != null && selectedType in availableTypes -> selectedType
            Category.Type.EXPENSE in availableTypes -> Category.Type.EXPENSE
            Category.Type.INCOME in availableTypes -> Category.Type.INCOME
            else -> Category.Type.EXPENSE
        }

        if (sortedCategories.isEmpty()) {
            CategoriesUiState.Empty(selectedType = resolvedSelectedType)
        } else {
            CategoriesUiState.Content(
                categories = sortedCategories,
                selectedType = resolvedSelectedType,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CategoriesUiState.Loading,
    )

    fun onAction(action: CategoriesAction) {
        when (action) {
            CategoriesAction.CreateDefaultCategories -> viewModelScope.launch {
                createDefaultCategories().onLeft {
                    crashlytics.recordException(it)
                }
            }

            is CategoriesAction.SelectType -> {
                selectedType.value = action.type
            }
        }
    }
}
