@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal.addCategory

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

class AddCategoryViewModel(
    private val repository: ICategoryRepository,
    private val modalManager: ModalManager,
    private val validateCategoryName: ValidateCategoryNameUseCase,
    private val debounceManager: DebounceManager,
) : ViewModel() {

    private val name = MutableStateFlow(FieldForm())

    private val type = MutableStateFlow(Category.Type.EXPENSE)

    private val icon = MutableStateFlow(CategoryIcon.SHOPPING_CART)

    val uiState = combine(name, type, icon) { name, type, icon ->
        AddCategoryUiState(
            name = name,
            selectedIcon = icon,
            selectedType = type,
            canSubmit = name.validation == Validation.Valid,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AddCategoryUiState()
    )

    fun onAction(action: AddCategoryAction) {
        when (action) {
            is AddCategoryAction.IconChanged -> {
                icon.value = action.icon
            }

            is AddCategoryAction.SelectedType -> {
                type.value = action.type
            }

            is AddCategoryAction.NameChanged -> {
                changeName(action)
            }

            is AddCategoryAction.Submit -> {
                submit()
            }
        }
    }

    private fun changeName(
        action: AddCategoryAction.NameChanged
    ) {
        name.update {
            it.copy(
                text = action.name,
                validation = Validation.Validating,
            )
        }

        debounceManager(
            scope = viewModelScope,
            key = "validate_category_name",
        ) {
            name.update {
                it.copy(
                    text = action.name,
                    validation = validateCategoryName(action.name)?.let { error ->
                        Validation.Error(error)
                    } ?: Validation.Valid,
                )
            }
        }
    }

    private fun submit() = viewModelScope.launch {

        if (validateCategoryName(name.value.text) != null) {
            return@launch
        }

        repository.insert(
            Category(
                name = name.value.text,
                icon = CategoryLazyIcon(icon.value.key),
                type = type.value,
                createdAt = Clock.System.now().toEpochMilliseconds()
            )
        )

        modalManager.dismiss()
    }
}

