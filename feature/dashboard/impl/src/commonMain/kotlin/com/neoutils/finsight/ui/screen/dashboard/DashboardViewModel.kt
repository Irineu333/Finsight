@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.EnterDashboardEditMode
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.analytics.event.SaveDashboardLayout
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.DashboardComponentPreference
import com.neoutils.finsight.domain.repository.*
import com.neoutils.finsight.domain.usecase.BuildDashboardViewingUseCase
import com.neoutils.finsight.domain.usecase.EnsureDefaultAccountUseCase
import com.neoutils.finsight.domain.usecase.ObserveConsolidationChangesUseCase
import com.neoutils.finsight.domain.usecase.GetDashboardPreferencesUseCase
import com.neoutils.finsight.extension.combine
import com.neoutils.finsight.ui.model.TransactionFacadeLookup
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import com.neoutils.finsight.extension.toYearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class DashboardViewModel(
    private val transactionRepository: ITransactionRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val accountRepository: IAccountRepository,
    private val budgetRepository: IBudgetRepository,
    private val recurringRepository: IRecurringRepository,
    private val recurringOccurrenceRepository: IRecurringOccurrenceRepository,
    private val categoryRepository: ICategoryRepository,
    private val installmentRepository: IInstallmentRepository,
    private val ensureDefaultAccountUseCase: EnsureDefaultAccountUseCase,
    private val getDashboardPreferences: GetDashboardPreferencesUseCase,
    private val buildDashboardViewingUseCase: BuildDashboardViewingUseCase,
    private val dashboardPreferencesRepository: IDashboardPreferencesRepository,
    private val observeConsolidationChanges: ObserveConsolidationChangesUseCase,
    private val dashboardPreviewFactory: DashboardPreviewFactory,
    private val baseCurrencyRepository: IBaseCurrencyRepository,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
    private val clock: Clock,
) : ViewModel() {

    init {
        viewModelScope.launch {
            ensureDefaultAccountUseCase().onLeft {
                crashlytics.recordException(it)
            }
        }
    }

    private val instant get() = clock.now()

    private val invoices = invoiceRepository
        .observeUnpaidInvoices()
        .map { invoices ->
            invoices.associateBy { it.creditCard.id }
        }

    private val preferences = getDashboardPreferences()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList(),
        )

    private val editingState = MutableStateFlow<DashboardUiState.Editing?>(null)

    /**
     * The transactions plus what the ledger cannot name for them: the category
     * behind a leg's dimension and the installment behind its id (design D6).
     * Combined into one flow because they always travel together: nothing below
     * ever reads one of them without the others.
     */
    private val transactionsWithFacades = combine(
        transactionRepository.observeAllTransactions(),
        categoryRepository.observeAllCategoriesIncludingClosed(),
        installmentRepository.observeAllInstallments(),
        // The total balance of this screen is the app's most consolidated figure, and a
        // rate is what turns two currencies into it — but registering a rate writes no
        // entry, so the ledger's own trigger never reaches here. Fused into this flow
        // rather than added below because it is a reason to recompute the very same
        // figures, and not a source of its own.
        observeConsolidationChanges(),
    ) { transactions, categories, installments, _ ->
        transactions to TransactionFacadeLookup.of(categories, installments)
    }

    private val viewingState: Flow<DashboardUiState> = combine(
        invoices,
        transactionsWithFacades,
        creditCardRepository.observeAllCreditCards(),
        accountRepository.observeAllAccounts(),
        budgetRepository.observeAllBudgets(),
        recurringRepository.observeAllRecurring(),
        recurringOccurrenceRepository.observeAllOccurrences(),
        dashboardPreferencesRepository.observeEditTipDismissed(),
        preferences,
    ) { invoices, transactionsAndFacades, creditCards, accounts, budgets, recurringList, occurrences, editTipDismissed, preferences ->
        val (transactions, facadeLookup) = transactionsAndFacades
        val today = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date

        val items = buildDashboardViewingUseCase(
            input = DashboardComponentsInput(
                transactions = transactions,
                creditCards = creditCards,
                invoicesByCreditCardId = invoices,
                accounts = accounts,
                budgets = budgets,
                recurringList = recurringList,
                occurrences = occurrences,
                today = today,
                targetMonth = today.yearMonth,
                facadeLookup = facadeLookup,
                baseCurrency = baseCurrencyRepository.observe().value,
            ),
            preferences = preferences,
        )

        if (items.isEmpty()) {
            DashboardUiState.Empty(
                yearMonth = today.yearMonth,
                accounts = accounts,
                creditCards = creditCards,
            )
        } else {
            DashboardUiState.Viewing(
                yearMonth = today.yearMonth,
                items = items,
                showEditTip = !editTipDismissed,
                accounts = accounts,
                creditCards = creditCards,
            )
        }
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        editingState,
        viewingState,
    ) { editing, viewing ->
        editing ?: viewing
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState.Loading(instant.toYearMonth()),
    )

    fun onAction(action: DashboardAction) = when (action) {
        is DashboardAction.EnterEditMode -> {
            enterEditMode()
        }

        is DashboardAction.ConfirmEdit -> {
            confirmEdit()
        }

        is DashboardAction.CancelEdit -> {
            editingState.value = null
        }

        is DashboardAction.RemoveAllComponents -> removeAllComponents()

        is DashboardAction.AddAllComponents -> addAllComponents()

        is DashboardAction.MoveComponent -> {
            moveComponent(action.fromKey, action.toKey)
        }

        is DashboardAction.UpdateComponentConfig -> {
            updateComponentConfig(action.key, action.config)
        }
    }

    private fun enterEditMode() {
        val current = uiState.value
        viewModelScope.launch {
            dashboardPreferencesRepository.dismissEditTip()
            when (current) {
                is DashboardUiState.Viewing ->
                    openEditingState(
                        yearMonth = current.yearMonth,
                        accounts = current.accounts,
                        creditCards = current.creditCards,
                    )

                is DashboardUiState.Empty ->
                    openEditingState(
                        yearMonth = current.yearMonth,
                        accounts = current.accounts,
                        creditCards = current.creditCards,
                    )

                else -> Unit
            }
        }
    }

    private suspend fun openEditingState(
        yearMonth: YearMonth,
        accounts: List<Account>,
        creditCards: List<CreditCard>,
    ) {
        editingState.value = buildEditingState(
            yearMonth = yearMonth,
            accounts = accounts,
            creditCards = creditCards,
            preferences = preferences.value,
        )
        analytics.logEvent(EnterDashboardEditMode)
    }

    private fun confirmEdit() = viewModelScope.launch {
        val editing = editingState.value ?: return@launch
        val prefs = editing.activeItems.mapIndexed { i, item ->
            DashboardComponentPreference(
                key = item.key,
                position = i, config = item.config
            )
        }
        dashboardPreferencesRepository.save(prefs)
        analytics.logEvent(SaveDashboardLayout(editing.activeItems.joinToString(",") { it.key }))
        editingState.value = null
    }

    private fun moveComponent(fromKey: String, toKey: String) {
        val current = editingState.value ?: return

        val moved = DashboardEditLayout(
            activeItems = current.activeItems,
            availableItems = current.availableItems,
        ).move(fromKey, toKey)

        editingState.value = current.copy(
            activeItems = moved.activeItems,
            availableItems = moved.availableItems,
        )
    }

    private fun removeAllComponents() {
        val current = editingState.value ?: return
        editingState.value = current.copy(
            activeItems = emptyList(),
            availableItems = current.activeItems + current.availableItems,
        )
    }

    private fun addAllComponents() {
        val current = editingState.value ?: return
        editingState.value = current.copy(
            activeItems = current.activeItems + current.availableItems,
            availableItems = emptyList(),
        )
    }

    private fun updateComponentConfig(
        key: String,
        config: Map<String, String>
    ) {
        val current = editingState.value ?: return

        editingState.value = current.copy(
            activeItems = current.activeItems.map { item ->
                when (item.key) {
                    key -> item.copy(config = config)
                    else -> item
                }
            },
        )
    }

    private suspend fun buildEditingState(
        yearMonth: YearMonth,
        accounts: List<Account>,
        creditCards: List<CreditCard>,
        preferences: List<DashboardComponentPreference>,
    ): DashboardUiState.Editing {

        val activeItems = preferences.sortedBy {
            it.position
        }.mapNotNull { pref ->
            val preview = dashboardPreviewFactory.createPreview(pref.key, yearMonth.lastDay)
                ?: return@mapNotNull null

            DashboardEditItem(
                preview = preview,
                config = pref.config,
            )
        }

        val presentKeys = preferences.map { it.key }.toSet()

        val availableItems = DashboardComponentType.entries
            .filterNot { it.key in presentKeys }
            .mapNotNull { entry ->
                val preview = dashboardPreviewFactory.createPreview(entry.key, yearMonth.lastDay)
                    ?: return@mapNotNull null
                DashboardEditItem(
                    preview = preview,
                    config = entry.defaultConfig,
                )
            }

        return DashboardUiState.Editing(
            yearMonth = yearMonth,
            activeItems = activeItems,
            availableItems = availableItems,
            accounts = accounts,
            creditCards = creditCards,
        )
    }
}
