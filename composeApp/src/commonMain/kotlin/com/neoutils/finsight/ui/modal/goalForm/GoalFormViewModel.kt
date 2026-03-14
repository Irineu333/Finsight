@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.goalForm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.getOrElse
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.model.Goal
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.repository.IGoalRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.usecase.ValidateGoalTitleUseCase
import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.extension.moneyToDouble
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.util.AppIcon
import com.neoutils.finsight.util.DebounceManager
import com.neoutils.finsight.util.ObservableMutableMap
import com.neoutils.finsight.util.Validation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class GoalFormViewModel(
    private val formatter: CurrencyFormatter,
    private val goal: Goal? = null,
    private val goalRepository: IGoalRepository,
    private val categoryRepository: ICategoryRepository,
    private val validateGoalTitle: ValidateGoalTitleUseCase,
    private val modalManager: ModalManager,
    private val debounceManager: DebounceManager,
) : ViewModel() {

    private val isEditMode = goal != null

    private val selectedCategories = MutableStateFlow<List<Category>>(goal?.categories ?: emptyList())
    private val selectedIcon = MutableStateFlow(AppIcon.fromKey(goal?.iconKey ?: AppIcon.GOAL.key))
    private val title = MutableStateFlow(goal?.title ?: "")
    private val amount = MutableStateFlow(goal?.amount?.let { formatter.format(it) } ?: "")
    private val validation = ObservableMutableMap(
        map = mutableMapOf(
            if (isEditMode) {
                GoalField.TITLE to Validation.Valid
            } else {
                GoalField.TITLE to Validation.Waiting
            }
        )
    )

    private data class FormFields(
        val selectedCategories: List<Category>,
        val selectedIcon: AppIcon,
        val title: String,
        val amount: String,
    )

    val uiState = combine(
        categoryRepository.observeCategoriesByType(Category.Type.INCOME),
        goalRepository.observeAllGoals(),
        combine(selectedCategories, selectedIcon, title, amount) { categories, icon, title, amount ->
            FormFields(categories, icon, title, amount)
        },
        validation,
    ) { categories, goals, fields, validation ->
        val targetedCategoryIds = goals
            .filter { it.id != goal?.id }
            .flatMap { it.categories }
            .map { it.id }
            .toSet()

        GoalFormUiState(
            availableCategories = categories.filter { it.id !in targetedCategoryIds },
            selectedCategories = fields.selectedCategories,
            selectedIcon = fields.selectedIcon,
            title = fields.title,
            amount = fields.amount,
            validation = validation,
            isEditMode = isEditMode,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GoalFormUiState(
            selectedCategories = goal?.categories ?: emptyList(),
            selectedIcon = AppIcon.fromKey(goal?.iconKey ?: AppIcon.GOAL.key),
            title = goal?.title ?: "",
            amount = goal?.amount?.let { formatter.format(it) } ?: "",
            validation = validation,
            isEditMode = isEditMode,
        ),
    )

    fun onAction(action: GoalFormAction) {
        when (action) {
            is GoalFormAction.TitleChanged -> changeTitle(action.title)
            is GoalFormAction.CategoryToggled -> {
                val isRemoving = selectedCategories.value.any { it.id == action.category.id }
                selectedCategories.update { current ->
                    if (isRemoving) {
                        current.filter { it.id != action.category.id }
                    } else {
                        current + action.category
                    }
                }
            }

            is GoalFormAction.AmountChanged -> amount.update { action.amount }
            is GoalFormAction.IconSelected -> selectedIcon.update { action.icon }
            GoalFormAction.Submit -> submit()
        }
    }

    private fun changeTitle(newTitle: String) {
        title.value = newTitle
        validation[GoalField.TITLE] = Validation.Validating

        debounceManager(
            scope = viewModelScope,
            key = "validate_goal_title",
        ) {
            validation[GoalField.TITLE] = validateGoalTitle(
                title = newTitle,
                ignoreId = goal?.id,
            ).map {
                Validation.Valid
            }.getOrElse {
                Validation.Error(it.toUiText())
            }
        }
    }

    private fun submit() {
        viewModelScope.launch {
            val validatedTitle = validateGoalTitle(
                title = title.value,
                ignoreId = goal?.id,
            ).getOrElse {
                validation[GoalField.TITLE] = Validation.Error(it.toUiText())
                return@launch
            }

            val state = uiState.value
            if (!state.canSubmit) return@launch

            if (goal != null) {
                goalRepository.update(
                    goal.copy(
                        title = validatedTitle.trim(),
                        categories = state.selectedCategories,
                        iconKey = state.selectedIcon.key,
                        amount = state.amount.moneyToDouble(),
                    )
                )
            } else {
                goalRepository.insert(
                    Goal(
                        title = validatedTitle.trim(),
                        categories = state.selectedCategories,
                        iconKey = state.selectedIcon.key,
                        amount = state.amount.moneyToDouble(),
                        createdAt = Clock.System.now().toEpochMilliseconds(),
                    )
                )
            }
            modalManager.dismissAll()
        }
    }
}
