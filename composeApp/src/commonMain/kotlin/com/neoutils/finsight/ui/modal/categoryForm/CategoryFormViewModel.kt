@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.categoryForm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.getOrElse
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.CreateCategory
import com.neoutils.finsight.domain.analytics.event.EditCategory
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.usecase.ValidateCategoryNameUseCase
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.util.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class CategoryFormViewModel(
    private val category: Category?,
    private val initialType: Category.Type?,
    private val repository: ICategoryRepository,
    private val validateCategoryName: ValidateCategoryNameUseCase,
    private val modalManager: ModalManager,
    private val debounceManager: DebounceManager,
    private val analytics: Analytics,
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
        category?.type ?: initialType ?: Category.Type.EXPENSE
    )

    private val icon = MutableStateFlow(
        if (category != null) {
            AppIcon.fromKey(category.iconKey)
        } else {
            AppIcon.CATEGORY
        }
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
            is CategoryFormAction.NameChanged -> {
                changeName(action.name)
            }
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
            ).map {
                Validation.Valid
            }.getOrElse {
                Validation.Error(it.toUiText())
            }
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
                    iconKey = icon.value.key
                )
            )
            analytics.logEvent(EditCategory(name.trim(), category.type))
            modalManager.dismissAll()
            return@launch
        }

        repository.insert(
            Category(
                name = name.trim(),
                iconKey = icon.value.key,
                type = type.value,
                createdAt = Clock.System.now().toEpochMilliseconds()
            )
        )
        analytics.logEvent(CreateCategory(name.trim(), type.value))
        modalManager.dismiss()
    }
}
