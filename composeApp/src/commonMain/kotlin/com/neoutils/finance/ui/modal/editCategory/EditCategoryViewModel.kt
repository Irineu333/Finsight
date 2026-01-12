@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal.editCategory

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
import kotlin.time.ExperimentalTime

class EditCategoryViewModel(
    private val category: Category,
    private val repository: ICategoryRepository,
    private val validateCategoryName: ValidateCategoryNameUseCase,
    private val modalManager: ModalManager,
    private val debounceManager: DebounceManager,
) : ViewModel() {

    private val name = MutableStateFlow(
        FieldForm(
            text = category.name,
            validation = Validation.Valid
        )
    )

    private val icon = MutableStateFlow(
        CategoryIcon.fromKey(category.icon.key)
    )

    val uiState = combine(name, icon) { name, icon ->
        EditCategoryUiState(
            name = name,
            selectedIcon = icon,
            selectedType = category.type,
            canSubmit = name.validation == Validation.Valid,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EditCategoryUiState(
            name = FieldForm(
                text = category.name,
                validation = Validation.Valid
            ),
            selectedIcon = CategoryIcon.fromKey(category.icon.key),
            selectedType = category.type,
            canSubmit = true,
        )
    )

    fun onAction(action: EditCategoryAction) {
        when (action) {
            is EditCategoryAction.NameChanged -> {
                changeName(action.name)
            }

            is EditCategoryAction.IconChanged -> {
                icon.value = action.icon
            }

            is EditCategoryAction.Submit -> {
                submit()
            }
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
                        ignoreId = category.id
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
            ignoreId = category.id
        )?.let {
            return@launch
        }

        repository.update(
            category.copy(
                name = name.value.text.trim(),
                icon = CategoryLazyIcon(icon.value.key)
            )
        )

        modalManager.dismissAll()
    }
}
