@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.budgetForm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.getOrElse
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.usecase.ValidateBudgetTitleUseCase
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

class BudgetFormViewModel(
    private val formatter: CurrencyFormatter,
    private val budget: Budget? = null,
    private val budgetRepository: IBudgetRepository,
    private val categoryRepository: ICategoryRepository,
    private val validateBudgetTitle: ValidateBudgetTitleUseCase,
    private val modalManager: ModalManager,
    private val debounceManager: DebounceManager,
) : ViewModel() {

    private val isEditMode = budget != null

    private val selectedCategories = MutableStateFlow<List<Category>>(budget?.categories ?: emptyList())
    private val selectedIcon = MutableStateFlow(AppIcon.fromKey(budget?.iconKey ?: AppIcon.BUDGET.key))
    private val title = MutableStateFlow(budget?.title ?: "")
    private val amount = MutableStateFlow(budget?.amount?.let { formatter.format(it) } ?: "")
    private val validation = ObservableMutableMap(
        map = mutableMapOf(
            if (isEditMode) {
                BudgetField.TITLE to Validation.Valid
            } else {
                BudgetField.TITLE to Validation.Waiting
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
        categoryRepository.observeCategoriesByType(Category.Type.EXPENSE),
        budgetRepository.observeAllBudgets(),
        combine(selectedCategories, selectedIcon, title, amount) { categories, icon, title, amount ->
            FormFields(categories, icon, title, amount)
        },
        validation,
    ) { categories, budgets, fields, validation ->
        val budgetedCategoryIds = budgets
            .filter { it.id != budget?.id }
            .flatMap { it.categories }
            .map { it.id }
            .toSet()

        BudgetFormUiState(
            availableCategories = categories.filter { it.id !in budgetedCategoryIds },
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
        initialValue = BudgetFormUiState(
            selectedCategories = budget?.categories ?: emptyList(),
            selectedIcon = AppIcon.fromKey(budget?.iconKey ?: AppIcon.BUDGET.key),
            title = budget?.title ?: "",
            amount = budget?.amount?.let { formatter.format(it) } ?: "",
            validation = validation,
            isEditMode = isEditMode,
        ),
    )

    fun onAction(action: BudgetFormAction) {
        when (action) {
            is BudgetFormAction.TitleChanged -> changeTitle(action.title)
            is BudgetFormAction.CategoryToggled -> {
                val isRemoving = selectedCategories.value.any { it.id == action.category.id }
                selectedCategories.update { current ->
                    if (isRemoving) {
                        current.filter { it.id != action.category.id }
                    } else {
                        current + action.category
                    }
                }
            }

            is BudgetFormAction.AmountChanged -> amount.update { action.amount }
            is BudgetFormAction.IconSelected -> selectedIcon.update { action.icon }
            BudgetFormAction.Submit -> submit()
        }
    }

    private fun changeTitle(newTitle: String) {
        title.value = newTitle
        validation[BudgetField.TITLE] = Validation.Validating

        debounceManager(
            scope = viewModelScope,
            key = "validate_budget_title",
        ) {
            validation[BudgetField.TITLE] = validateBudgetTitle(
                title = newTitle,
                ignoreId = budget?.id,
            ).map {
                Validation.Valid
            }.getOrElse {
                Validation.Error(it.toUiText())
            }
        }
    }

    private fun submit() {
        viewModelScope.launch {
            val validatedTitle = validateBudgetTitle(
                title = title.value,
                ignoreId = budget?.id,
            ).getOrElse {
                validation[BudgetField.TITLE] = Validation.Error(it.toUiText())
                return@launch
            }

            val state = uiState.value
            if (!state.canSubmit) return@launch

            if (budget != null) {
                budgetRepository.update(
                    budget.copy(
                        title = validatedTitle.trim(),
                        categories = state.selectedCategories,
                        iconKey = state.selectedIcon.key,
                        amount = state.amount.moneyToDouble(),
                    )
                )
            } else {
                budgetRepository.insert(
                    Budget(
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
