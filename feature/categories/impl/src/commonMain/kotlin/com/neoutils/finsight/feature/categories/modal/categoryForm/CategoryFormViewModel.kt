@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.feature.categories.modal.categoryForm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.getOrElse
import com.neoutils.finsight.feature.categories.extension.toUiText
import com.neoutils.finsight.core.analytics.Analytics
import com.neoutils.finsight.core.analytics.crashlytics.Crashlytics
import com.neoutils.finsight.feature.categories.event.CreateCategory
import com.neoutils.finsight.feature.categories.event.EditCategory
import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.feature.categories.repository.ICategoryRepository
import com.neoutils.finsight.feature.categories.usecase.ValidateCategoryNameUseCase
import com.neoutils.finsight.core.ui.component.ModalManager
import com.neoutils.finsight.core.ui.util.AppIcon
import com.neoutils.finsight.core.ui.util.Validation
import com.neoutils.finsight.core.utils.util.DebounceManager
import com.neoutils.finsight.core.utils.util.ObservableMutableMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class CategoryFormViewModel(
    private val categoryId: Long?,
    initialType: Category.Type?,
    private val repository: ICategoryRepository,
    private val validateCategoryName: ValidateCategoryNameUseCase,
    private val modalManager: ModalManager,
    private val debounceManager: DebounceManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val isEditMode = categoryId != null

    private val loadedCategory = MutableStateFlow<Category?>(null)
    private val isReady = MutableStateFlow(!isEditMode)

    private val name = MutableStateFlow("")
    private val type = MutableStateFlow(initialType ?: Category.Type.EXPENSE)
    private val icon = MutableStateFlow(AppIcon.CATEGORY)

    private val validation = ObservableMutableMap(
        map = mutableMapOf(
            CategoryField.NAME to if (isEditMode) Validation.Valid else Validation.Waiting,
        )
    )

    init {
        if (categoryId != null) {
            viewModelScope.launch {
                val category = repository.getCategoryById(categoryId)
                if (category == null) {
                    crashlytics.recordException(
                        IllegalStateException("Category $categoryId not found")
                    )
                    modalManager.dismiss()
                    return@launch
                }
                loadedCategory.value = category
                name.value = category.name
                type.value = category.type
                icon.value = AppIcon.fromKey(category.iconKey)
                isReady.value = true
            }
        }
    }

    val uiState = combine(
        isReady,
        name,
        type,
        icon,
        validation,
    ) { ready, name, type, icon, validation ->
        if (!ready) {
            CategoryFormUiState.Loading
        } else {
            CategoryFormUiState.Content(
                name = name,
                validation = validation,
                selectedIcon = icon,
                selectedType = type,
                isEditMode = isEditMode,
                canSubmit = validation[CategoryField.NAME] == Validation.Valid,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = if (isEditMode) {
            CategoryFormUiState.Loading
        } else {
            CategoryFormUiState.Content(
                name = name.value,
                validation = validation,
                selectedIcon = icon.value,
                selectedType = type.value,
                isEditMode = false,
                canSubmit = validation[CategoryField.NAME] == Validation.Valid,
            )
        },
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
                ignoreId = categoryId,
            ).map {
                Validation.Valid
            }.getOrElse {
                Validation.Error(it.toUiText())
            }
        }
    }

    private fun submit() = viewModelScope.launch {

        val validatedName = validateCategoryName(
            name = name.value,
            ignoreId = categoryId,
        ).getOrElse {
            return@launch
        }

        val loaded = loadedCategory.value
        if (loaded != null) {
            repository.update(
                loaded.copy(
                    name = validatedName.trim(),
                    iconKey = icon.value.key,
                )
            )
            analytics.logEvent(EditCategory(validatedName.trim(), loaded.type))
            modalManager.dismissAll()
            return@launch
        }

        repository.insert(
            Category(
                name = validatedName.trim(),
                iconKey = icon.value.key,
                type = type.value,
                createdAt = Clock.System.now().toEpochMilliseconds(),
            )
        )
        analytics.logEvent(CreateCategory(validatedName.trim(), type.value))
        modalManager.dismiss()
    }
}
