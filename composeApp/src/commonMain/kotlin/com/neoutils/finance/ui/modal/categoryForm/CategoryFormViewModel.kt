@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal.categoryForm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.usecase.ValidateCategoryNameUseCase
import com.neoutils.finance.ui.component.ModalManager
import com.neoutils.finance.ui.icons.CategoryLazyIcon
import com.neoutils.finance.util.ObservableMutableMap
import com.neoutils.finance.util.CategoryIcon
import com.neoutils.finance.util.DebounceManager
import com.neoutils.finance.util.Validation
import com.neoutils.finance.util.validation
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class CategoryFormViewModel(
    private val category: Category?,
    private val repository: ICategoryRepository,
    private val validateCategoryName: ValidateCategoryNameUseCase,
    private val modalManager: ModalManager,
    private val debounceManager: DebounceManager,
) : ViewModel() {

    private val isEditMode = category != null

    private val name = MutableStateFlow(category?.name.orEmpty())

    private val validation = ObservableMutableMap(
        map = mutableMapOf(
            if (isEditMode) {
                CategoryField.NAME to Validation.Valid
            } else {
                CategoryField.NAME to Validation.Waiting
            }
        )
    )

    private val type = MutableStateFlow(
        category?.type ?: Category.Type.EXPENSE
    )

    private val icon = MutableStateFlow(
        category?.icon?.key?.let { CategoryIcon.fromKey(it) }
            ?: CategoryIcon.SHOPPING_CART
    )

    val uiState = combine(name, type, icon, validation) { name, type, icon, validation ->
        CategoryFormUiState(
            name = name,
            validation = validation,
            selectedIcon = icon,
            selectedType = type,
            isEditMode = isEditMode,
            canSubmit = validation[CategoryField.NAME] == Validation.Valid,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CategoryFormUiState(
            name = name.value,
            validation = validation,
            selectedIcon = icon.value,
            selectedType = type.value,
            isEditMode = isEditMode,
            canSubmit = validation[CategoryField.NAME] == Validation.Valid,
        )
    )

    fun onAction(action: CategoryFormAction) {
        when (action) {
            is CategoryFormAction.NameChanged -> changeName(action.name)
            is CategoryFormAction.TypeChanged -> {
                type.value = action.type
            }

            is CategoryFormAction.IconChanged -> {
                icon.value = action.icon
            }

            is CategoryFormAction.Submit -> submit()
        }
    }

    private fun changeName(newName: String) {
        name.value = newName
        validation[CategoryField.NAME] = Validation.Validating

        debounceManager(
            scope = viewModelScope,
            key = "validate_category_name",
        ) {
            validation[CategoryField.NAME] = validateCategoryName(
                name = newName,
                ignoreId = category?.id
            ).validation
        }
    }

    private fun submit() = viewModelScope.launch {

        val name = validateCategoryName(
            name = name.value,
            ignoreId = category?.id
        ).getOrElse {
            return@launch
        }

        if (category != null) {
            repository.update(
                category.copy(
                    name = name.trim(),
                    icon = CategoryLazyIcon(icon.value.key)
                )
            )
            modalManager.dismissAll()
            return@launch
        }

        repository.insert(
            Category(
                name = name.trim(),
                icon = CategoryLazyIcon(icon.value.key),
                type = type.value,
                createdAt = Clock.System.now().toEpochMilliseconds()
            )
        )
        modalManager.dismiss()
    }
}
