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
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.CreateBudget
import com.neoutils.finsight.domain.analytics.event.EditBudget
import com.neoutils.finsight.domain.model.CurrencyCatalog
import com.neoutils.finsight.domain.model.CurrencyInfo
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.usecase.AccountCurrencies
import com.neoutils.finsight.domain.usecase.GetAccountCurrenciesUseCase
import com.neoutils.finsight.domain.usecase.ValidateBudgetTitleUseCase
import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.extension.moneyToDouble
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.util.AppIcon
import com.neoutils.finsight.util.DebounceManager
import com.neoutils.finsight.util.ObservableMutableMap
import com.neoutils.finsight.util.Validation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class BudgetFormViewModel(
    private val formatter: CurrencyFormatter,
    private val budget: Budget? = null,
    private val budgetRepository: IBudgetRepository,
    // Which currencies the user holds — across accounts **and** cards, which is why it
    // is one use case and not a listing this form assembles: a user whose foreign
    // spending is all on a card is exactly the case D13 exists for, and the account
    // listing does not report that currency at all.
    private val getAccountCurrencies: GetAccountCurrenciesUseCase,
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
    // Only an existing budget seeds the field, and it is read back in the currency it was
    // created with — the denomination of a stored limit never changes (design D13).
    private val amount = MutableStateFlow(
        budget?.let { formatter.format(it.amount, it.currency) } ?: ""
    )
    private val limitType = MutableStateFlow(budget?.limitType ?: LimitType.FIXED)
    private val percentage = MutableStateFlow(budget?.percentage?.toString() ?: "")
    private val selectedRecurring = MutableStateFlow<Recurring?>(null)
    /**
     * What the limit is denominated in. Editing never re-reads it — the denomination of
     * a stored limit is fixed for the same reason an account's is (design D12/D13), and
     * reinterpreting it would rewrite in silence the meaning of a number the user typed.
     * Creating takes it from the **default account**, because that is where the user
     * actually transacts; the base currency is not the answer, since it only says in
     * which currency he reads totals.
     */
    private val accountCurrencies = flow { emit(getAccountCurrencies()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = AccountCurrencies(inUse = emptyList(), ofDefaultAccount = null),
        )

    private val pickedCurrency = MutableStateFlow<String?>(null)

    /**
     * The denomination of the limit, and **whether the user is asked about it at all**.
     *
     * The control is offered only when there is a choice to make (design D13): with one
     * currency across the user's accounts and cards, the form is exactly the one it has
     * always been — not a control more — and the limit takes that single currency,
     * which is the only possible answer rather than a silent default.
     *
     * This is why it differs from the account form, which shows its currency row even
     * with one currency (design D23): that form is the **door** a second currency is
     * born through and has to stay open, while this one only picks among the ones that
     * already exist.
     */
    private val currencyState: Flow<LimitCurrency> = combine(
        accountCurrencies,
        pickedCurrency,
    ) { currencies, picked ->
        limitCurrencyChoice(existing = budget, currencies = currencies, picked = picked)
    }

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
        currencyState,
    ) { categories, budgets, (allRecurrings, fields, validation), limitCurrency ->
        val budgetedCategoryIds = budgets
            .filter { it.id != budget?.id }
            .flatMap { it.categories }
            .map { it.id }
            .toSet()

        val incomeRecurrings = allRecurrings.filter { it.type == TransactionType.INCOME }

        // Resolved against every income recurring, archived included: the budget's own
        // base income is a link already established, and archiving governs the *new*
        // choice, not the one already made. Resolving inside the offered list instead
        // would erase it the moment the recurring was archived.
        val resolvedSelectedRecurring = fields.selectedRecurring
            ?: budget?.recurringId?.let { id -> incomeRecurrings.find { it.id == id } }

        BudgetFormUiState(
            availableCategories = offeredCategories(
                open = categories,
                selected = fields.selectedCategories,
                otherBudgetCategoryIds = budgetedCategoryIds,
            ),
            selectedCategories = fields.selectedCategories,
            selectedIcon = fields.selectedIcon,
            title = fields.title,
            amount = fields.amount,
            currency = limitCurrency.currency,
            canChangeCurrency = limitCurrency.canChange,
            selectableCurrencies = limitCurrency.selectable,
            validation = validation,
            isEditMode = isEditMode,
            limitType = fields.limitType,
            percentage = fields.percentage,
            incomeRecurrings = offeredRecurrings(
                open = incomeRecurrings.filterNot { it.isArchived },
                selected = resolvedSelectedRecurring,
            ),
            selectedRecurring = resolvedSelectedRecurring,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BudgetFormUiState(
            selectedCategories = budget?.categories ?: emptyList(),
            selectedIcon = AppIcon.fromKey(budget?.iconKey ?: AppIcon.BUDGET.key),
            title = budget?.title ?: "",
            amount = budget?.let { formatter.format(it.amount, it.currency) } ?: "",
            currency = budget?.currency,
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

            is BudgetFormAction.CurrencySelected -> {
                // Never while editing, and never when there was nothing to choose.
                if (!isEditMode) pickedCurrency.value = action.code
            }
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
            // Guaranteed by `canSubmit`; re-read because a limit must never be stored
            // with a denomination nobody chose.
            val currency = state.currency ?: return@launch

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
                        // `currency` is deliberately absent from this copy: the
                        // denomination of a stored limit never changes.
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
                        currency = currency,
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

/**
 * Continuity of an already-made choice, the single form both selections of this form
 * use: what is offered is [offered] **plus** whatever is already [chosen] and no
 * longer in it.
 *
 * A facade archived after it was chosen drops out of [offered], so without this it
 * would show in the field but could never be unpicked. It is not offered fresh — it
 * appears only because it is already chosen — and once dropped it is gone, since an
 * archived facade is never back in [offered] to be picked again.
 */
private fun <T> withAlreadyChosen(
    offered: List<T>,
    chosen: List<T>,
    id: (T) -> Long,
): List<T> = offered + chosen.filterNot { c -> offered.any { id(it) == id(c) } }

/**
 * The categories the form offers in its dropdown: the open ones, minus any already
 * claimed by another budget (a category belongs to at most one), kept continuous by
 * [withAlreadyChosen].
 */
internal fun offeredCategories(
    open: List<Category>,
    selected: List<Category>,
    otherBudgetCategoryIds: Set<Long>,
): List<Category> = withAlreadyChosen(
    offered = open.filterNot { it.id in otherBudgetCategoryIds },
    chosen = selected,
    id = Category::id,
)

/**
 * The recurrings the form offers as base income: the unarchived ones, kept continuous
 * by [withAlreadyChosen] so a budget that already elected one keeps seeing it — and
 * can swap it — after it is archived.
 */
internal fun offeredRecurrings(
    open: List<Recurring>,
    selected: Recurring?,
): List<Recurring> = withAlreadyChosen(
    offered = open,
    chosen = listOfNotNull(selected),
    id = Recurring::id,
)

/**
 * What the limit is denominated in, and **whether the user is asked about it at all**.
 *
 * The two profiles design D13 is written around:
 *
 * | currencies across the user's accounts and cards | the form |
 * |---|---|
 * | one | **no control** — the limit takes it, the only possible answer |
 * | more than one | a picker, pre-selected with the default account's |
 *
 * The suggestion is the **default account's** currency and deliberately not the base:
 * the base answers in which currency the user reads totals, not the one he spends in.
 *
 * Editing never offers the choice, for the same reason an account's currency is
 * immutable (design D12): reinterpreting a stored limit rewrites in silence the meaning
 * of a number the user typed. Changing it is creating another budget.
 *
 * And this is why the rule differs from D23, which keeps the account form's currency row
 * visible even with one currency: that form is the **door** a second currency is born
 * through and has to stay open; this one only picks among the ones that already exist.
 */
internal fun limitCurrencyChoice(
    existing: Budget?,
    currencies: AccountCurrencies,
    picked: String?,
): LimitCurrency = LimitCurrency(
    currency = existing?.currency ?: picked ?: currencies.ofDefaultAccount,
    canChange = existing == null && currencies.inUse.size > 1,
    selectable = CurrencyCatalog.currencies.filter { it.code in currencies.inUse },
)

internal data class LimitCurrency(
    val currency: String?,
    val canChange: Boolean,
    val selectable: List<CurrencyInfo>,
)
