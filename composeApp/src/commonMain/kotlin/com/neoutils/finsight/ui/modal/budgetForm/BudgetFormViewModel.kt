@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.budgetForm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.getOrElse
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.LimitType
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.CreateBudget
import com.neoutils.finsight.domain.analytics.event.EditBudget
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
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
    private val recurringRepository: IRecurringRepository,
    private val validateBudgetTitle: ValidateBudgetTitleUseCase,
    private val modalManager: ModalManager,
    private val debounceManager: DebounceManager,
    private val analytics: Analytics,
) : ViewModel() {

    private val isEditMode = budget != null

    private val selectedCategories = MutableStateFlow<List<Category>>(budget?.categories ?: emptyList())
    private val selectedIcon = MutableStateFlow(AppIcon.fromKey(budget?.iconKey ?: AppIcon.BUDGET.key))
    private val title = MutableStateFlow(budget?.title ?: "")
    private val amount = MutableStateFlow(budget?.amount?.let { formatter.format(it) } ?: "")
    private val limitType = MutableStateFlow(budget?.limitType ?: LimitType.FIXED)
    private val percentage = MutableStateFlow(budget?.percentage?.toString() ?: "")
    private val selectedRecurring = MutableStateFlow<Recurring?>(null)
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
        val limitType: LimitType,
        val percentage: String,
        val selectedRecurring: Recurring?,
    )

    private val formFields = combine(
        combine(selectedCategories, selectedIcon, title, amount) { cats, icon, t, amt ->
            cats to Triple(icon, t, amt)
        },
        combine(limitType, percentage, selectedRecurring) { lt, pct, rec ->
            Triple(lt, pct, rec)
        },
    ) { (cats, iconTitleAmount), (lt, pct, rec) ->
        val (icon, t, amt) = iconTitleAmount
        FormFields(cats, icon, t, amt, lt, pct, rec)
    }

    val uiState = combine(
        categoryRepository.observeCategoriesByType(Category.Type.EXPENSE),
        budgetRepository.observeAllBudgets(),
        combine(recurringRepository.observeAllRecurring(), formFields, validation) { rec, fields, v ->
            Triple(rec, fields, v)
        },
    ) { categories, budgets, (allRecurrings, fields, validation) ->
        val budgetedCategoryIds = budgets
            .filter { it.id != budget?.id }
            .flatMap { it.categories }
            .map { it.id }
            .toSet()

        val incomeRecurrings = allRecurrings.filter { it.type == Recurring.Type.INCOME && it.isActive }

        val resolvedSelectedRecurring = fields.selectedRecurring
            ?: budget?.recurringId?.let { id -> incomeRecurrings.find { it.id == id } }

        BudgetFormUiState(
            availableCategories = categories.filter { it.id !in budgetedCategoryIds },
            selectedCategories = fields.selectedCategories,
            selectedIcon = fields.selectedIcon,
            title = fields.title,
            amount = fields.amount,
            validation = validation,
            isEditMode = isEditMode,
            limitType = fields.limitType,
            percentage = fields.percentage,
            incomeRecurrings = incomeRecurrings,
            selectedRecurring = resolvedSelectedRecurring,
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
            limitType = budget?.limitType ?: LimitType.FIXED,
            percentage = budget?.percentage?.toString() ?: "",
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
            is BudgetFormAction.LimitTypeChanged -> limitType.update { action.limitType }
            is BudgetFormAction.PercentageChanged -> percentage.update { action.percentage }
            is BudgetFormAction.RecurringSelected -> selectedRecurring.update { action.recurring }
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

            val resolvedAmount = when (state.limitType) {
                LimitType.FIXED -> state.amount.moneyToDouble()
                LimitType.PERCENTAGE -> {
                    val rec = state.selectedRecurring ?: return@launch
                    rec.amount * (state.percentage.toDoubleOrNull() ?: 0.0) / 100.0
                }
            }

            if (budget != null) {
                budgetRepository.update(
                    budget.copy(
                        title = validatedTitle.trim(),
                        categories = state.selectedCategories,
                        iconKey = state.selectedIcon.key,
                        amount = resolvedAmount,
                        limitType = state.limitType,
                        percentage = if (state.limitType == LimitType.PERCENTAGE) state.percentage.toDoubleOrNull() else null,
                        recurringId = if (state.limitType == LimitType.PERCENTAGE) state.selectedRecurring?.id else null,
                    )
                )
            } else {
                budgetRepository.insert(
                    Budget(
                        title = validatedTitle.trim(),
                        categories = state.selectedCategories,
                        iconKey = state.selectedIcon.key,
                        amount = resolvedAmount,
                        limitType = state.limitType,
                        percentage = if (state.limitType == LimitType.PERCENTAGE) state.percentage.toDoubleOrNull() else null,
                        recurringId = if (state.limitType == LimitType.PERCENTAGE) state.selectedRecurring?.id else null,
                        createdAt = Clock.System.now().toEpochMilliseconds(),
                    )
                )
            }
            analytics.logEvent(
                if (budget != null) {
                    EditBudget(state.limitType, state.selectedCategories)
                } else {
                    CreateBudget(state.limitType, state.selectedCategories)
                }
            )
            modalManager.dismissAll()
        }
    }
}
