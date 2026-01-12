@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal.categoryForm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.usecase.ValidateCategoryNameUseCase
import com.neoutils.finance.ui.component.ModalManager
import com.neoutils.finance.ui.icons.CategoryLazyIcon
import com.neoutils.finance.util.CategoryIcon
import com.neoutils.finance.util.DebounceManager
import com.neoutils.finance.util.FieldForm
import com.neoutils.finance.util.Validation
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

    private val name = MutableStateFlow(
        FieldForm(
            text = category?.name.orEmpty(),
            validation = if (isEditMode) Validation.Valid else Validation.Waiting
        )
    )

    private val type = MutableStateFlow(
        category?.type ?: Category.Type.EXPENSE
    )

    private val icon = MutableStateFlow(
        category?.icon?.key?.let { CategoryIcon.fromKey(it) }
            ?: CategoryIcon.SHOPPING_CART
    )

    val uiState = combine(name, type, icon) { name, type, icon ->
        CategoryFormUiState(
            name = name,
            selectedIcon = icon,
            selectedType = type,
            isEditMode = isEditMode,
            canSubmit = name.validation == Validation.Valid,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CategoryFormUiState(
            name = name.value,
            selectedIcon = icon.value,
            selectedType = type.value,
            isEditMode = isEditMode,
            canSubmit = name.value.validation == Validation.Valid,
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
        name.update {
            it.copy(
                text = newName,
                validation = Validation.Validating,
            )
        }

        debounceManager(
            scope = viewModelScope,
            key = "validate_category_name",
        ) {
            name.update {
                it.copy(
                    validation = validateCategoryName(
                        name = newName,
                        ignoreId = category?.id
                    )?.let { error ->
                        Validation.Error(error)
                    } ?: Validation.Valid,
                )
            }
        }
    }

    private fun submit() = viewModelScope.launch {

        validateCategoryName(
            name = name.value.text,
            ignoreId = category?.id
        )?.let {
            return@launch
        }

        if (category != null) {
            repository.update(
                category.copy(
                    name = name.value.text.trim(),
                    icon = CategoryLazyIcon(icon.value.key)
                )
            )
            modalManager.dismissAll()
            return@launch
        }

        repository.insert(
            Category(
                name = name.value.text.trim(),
                icon = CategoryLazyIcon(icon.value.key),
                type = type.value,
                createdAt = Clock.System.now().toEpochMilliseconds()
            )
        )
        modalManager.dismiss()
    }
}
