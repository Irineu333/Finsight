package com.neoutils.finsight.feature.categories.modal.categoryForm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.getOrElse
import com.neoutils.finsight.feature.categories.extension.toUiText
import com.neoutils.finsight.core.analytics.Analytics
import com.neoutils.finsight.core.analytics.crashlytics.Crashlytics
import com.neoutils.finsight.feature.categories.error.CategoryError
import com.neoutils.finsight.feature.categories.event.CreateCategory
import com.neoutils.finsight.feature.categories.event.EditCategory
import com.neoutils.finsight.feature.categories.exception.CategoryException
import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.feature.categories.model.form.CategoryForm
import com.neoutils.finsight.feature.categories.repository.ICategoryRepository
import com.neoutils.finsight.feature.categories.usecase.ValidateCategoryNameUseCase
import com.neoutils.finsight.core.ui.component.ModalManager
import com.neoutils.finsight.core.ui.util.AppIcon
import com.neoutils.finsight.core.ui.util.Validation
import com.neoutils.finsight.core.utils.util.DebounceManager
import com.neoutils.finsight.core.utils.util.ObservableMutableMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryFormViewModel(
    private val categoryId: Long?,
    private val initialType: Category.Type?,
    private val repository: ICategoryRepository,
    private val validateCategoryName: ValidateCategoryNameUseCase,
    private val modalManager: ModalManager,
    private val debounceManager: DebounceManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val isEditMode = categoryId != null

    private val validation = ObservableMutableMap(
        map = mutableMapOf(
            CategoryField.NAME to if (isEditMode) {
                Validation.Valid
            } else {
                Validation.Waiting
            }
        )
    )

    private val form = MutableStateFlow<CategoryForm?>(null)
    private val notFound = MutableStateFlow(false)

    init {
        setup()
    }

    private fun setup() = viewModelScope.launch {
        if (categoryId != null) {
            form.value = repository.getCategoryById(categoryId)?.let {
                CategoryForm(
                    id = it.id,
                    name = it.name,
                    type = it.type,
                    icon = AppIcon.fromKey(it.iconKey),
                    createdAt = it.createdAt,
                )
            } ?: run {
                crashlytics.recordException(CategoryException(CategoryError.NOT_FOUND))
                notFound.value = true
                return@launch
            }
        } else {
            form.value = CategoryForm(
                type = initialType ?: Category.Type.EXPENSE,
            )
        }
    }

    private val content = combine(
        form.filterNotNull(),
        validation,
    ) { form, validation ->
        CategoryFormUiState.Content(
            form = form,
            validation = validation,
            isEditMode = isEditMode,
            canSubmit = validation[CategoryField.NAME] == Validation.Valid,
        ) as CategoryFormUiState
    }

    val uiState = notFound.flatMapLatest { error ->
        if (error) flowOf(CategoryFormUiState.Error) else content
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CategoryFormUiState.Loading,
    )

    fun onAction(action: CategoryFormAction) {
        when (action) {
            is CategoryFormAction.NameChanged -> {
                changeName(action.name)
            }

            is CategoryFormAction.TypeChanged -> {
                form.update {
                    it?.copy(
                        type = action.type
                    )
                }
            }

            is CategoryFormAction.IconChanged -> {
                form.update {
                    it?.copy(
                        icon = action.icon
                    )
                }
            }

            is CategoryFormAction.Submit -> submit()
        }
    }

    private fun changeName(newName: String) {

        form.update { it?.copy(name = newName) }

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

        val form = form.value ?: return@launch

        val validatedName = validateCategoryName(
            name = form.name,
            ignoreId = categoryId,
        ).getOrElse {
            return@launch
        }

        val ready = form.copy(name = validatedName.trim())

        if (isEditMode) {
            repository.update(ready.build())
            analytics.logEvent(EditCategory(ready.name, ready.type))
            modalManager.dismissAll()
            return@launch
        }

        repository.insert(ready.build())
        analytics.logEvent(CreateCategory(ready.name, ready.type))
        modalManager.dismiss()
    }
}
