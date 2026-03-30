@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.DashboardComponentPreference
import com.neoutils.finsight.domain.repository.*
import com.neoutils.finsight.domain.usecase.EnsureDefaultAccountUseCase
import com.neoutils.finsight.extension.combine
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class DashboardViewModel(
    private val operationRepository: IOperationRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val accountRepository: IAccountRepository,
    private val budgetRepository: IBudgetRepository,
    private val recurringRepository: IRecurringRepository,
    private val recurringOccurrenceRepository: IRecurringOccurrenceRepository,
    private val ensureDefaultAccountUseCase: EnsureDefaultAccountUseCase,
    private val dashboardComponentsBuilder: DashboardComponentsBuilder,
    private val dashboardPreferencesRepository: IDashboardPreferencesRepository,
) : ViewModel() {

    init {
        viewModelScope.launch {
            ensureDefaultAccountUseCase()
        }
    }

    private val instant get() = Clock.System.now()

    private val invoices = invoiceRepository
        .observeUnpaidInvoices()
        .map { invoices -> invoices.associateBy { it.creditCard.id } }

    private val preferences: StateFlow<List<DashboardComponentPreference>?> =
        dashboardPreferencesRepository.observe()

    private val _editingState = MutableStateFlow<DashboardUiState.Editing?>(null)

    private val viewingState: Flow<DashboardUiState> = combine(
        invoices,
        operationRepository.observeAllOperations(),
        creditCardRepository.observeAllCreditCards(),
        accountRepository.observeAllAccounts(),
        budgetRepository.observeAllBudgets(),
        recurringRepository.observeAllRecurring(),
        recurringOccurrenceRepository.observeAllOccurrences(),
        preferences,
    ) { invoices, operations, creditCards, accounts, budgets, recurringList, occurrences, preferences ->
        val today = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
        val effectivePrefs = preferences ?: DashboardComponentRegistry.defaultPreferences()
        val configByKey = effectivePrefs.associate { it.key to it.config }

        val allComponents = dashboardComponentsBuilder.build(
            input = DashboardComponentsInput(
                operations = operations,
                creditCards = creditCards,
                invoicesByCreditCardId = invoices,
                accounts = accounts,
                budgets = budgets,
                recurringList = recurringList,
                occurrences = occurrences,
                today = today,
                targetMonth = today.yearMonth,
                configByKey = configByKey,
            ),
        )

        val ordered = applyPreferences(effectivePrefs, allComponents)

        if (ordered.isEmpty()) {
            DashboardUiState.Empty(
                yearMonth = today.yearMonth,
                accounts = accounts,
                creditCards = creditCards,
            )
        } else {
            DashboardUiState.Viewing(
                yearMonth = today.yearMonth,
                components = ordered,
                accounts = accounts,
                creditCards = creditCards,
                configByKey = configByKey,
            )
        }
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        _editingState,
        viewingState,
    ) { editing, viewing ->
        editing ?: viewing
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState.Loading(),
    )

    fun onAction(action: DashboardAction) = when (action) {
        is DashboardAction.EnterEditMode -> enterEditMode()
        is DashboardAction.ConfirmEdit -> confirmEdit()
        is DashboardAction.CancelEdit -> cancelEdit()
        is DashboardAction.MoveComponent -> moveComponent(action.fromKey, action.toKey)
        is DashboardAction.RemoveComponent -> removeComponent(action.key)
        is DashboardAction.UpdateComponentConfig -> updateComponentConfig(action.key, action.config)
    }

    private fun enterEditMode() {
        when (val current = uiState.value) {
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

    private fun openEditingState(
        yearMonth: YearMonth,
        accounts: List<Account>,
        creditCards: List<CreditCard>,
    ) {
        _editingState.value = buildEditingState(
            yearMonth = yearMonth,
            accounts = accounts,
            creditCards = creditCards,
            savedPrefs = preferences.value,
        )
    }

    private fun cancelEdit() {
        _editingState.value = null
    }

    private fun confirmEdit() {
        viewModelScope.launch {
            val editing = _editingState.value ?: return@launch
            val prefs = editing.items.mapIndexed { i, item ->
                DashboardComponentPreference(key = item.key, position = i, config = item.config)
            }
            dashboardPreferencesRepository.save(prefs)
            _editingState.value = null
        }
    }

    private fun moveComponent(fromKey: String, toKey: String) {
        val current = _editingState.value ?: return

        val allItems = current.items + current.availableItems
        val fromIndex = allItems.indexOfFirst { it.key == fromKey }.takeIf { it >= 0 } ?: return

        val activeCount = current.items.size

        when (toKey) {
            EDIT_SECTION_HEADER_KEY, EDIT_AVAILABLE_PLACEHOLDER_KEY -> {
                val fromInActive = fromIndex < activeCount
                val mutable = allItems.toMutableList()
                val moved = mutable.removeAt(fromIndex)
                if (fromInActive) {
                    val newActiveCount = activeCount - 1
                    mutable.add(newActiveCount, moved)
                    _editingState.value = current.copy(
                        items = mutable.take(newActiveCount),
                        availableItems = mutable.drop(newActiveCount),
                    )
                } else {
                    mutable.add(activeCount, moved)
                    val newActiveCount = activeCount + 1
                    _editingState.value = current.copy(
                        items = mutable.take(newActiveCount),
                        availableItems = mutable.drop(newActiveCount),
                    )
                }
            }

            else -> {
                val toIndex = allItems.indexOfFirst { it.key == toKey }.takeIf { it >= 0 } ?: return
                val fromInActive = fromIndex < activeCount
                val toInActive = toIndex < activeCount

                val mutable = allItems.toMutableList()
                val moved = mutable.removeAt(fromIndex)
                mutable.add(toIndex.coerceAtMost(mutable.size), moved)

                val newActiveCount = when {
                    fromInActive && !toInActive -> activeCount - 1
                    !fromInActive && toInActive -> activeCount + 1
                    else -> activeCount
                }
                _editingState.value = current.copy(
                    items = mutable.take(newActiveCount),
                    availableItems = mutable.drop(newActiveCount),
                )
            }
        }
    }

    private fun removeComponent(key: String) {
        val current = _editingState.value ?: return
        val removed = current.items.find { it.key == key } ?: return
        _editingState.value = current.copy(
            items = current.items.filter { it.key != key },
            availableItems = current.availableItems + removed,
        )
    }

    private fun updateComponentConfig(key: String, config: Map<String, String>) {
        val current = _editingState.value ?: return
        _editingState.value = current.copy(
            items = current.items.map { if (it.key == key) it.copy(config = config) else it },
        )
    }

    private fun buildEditingState(
        yearMonth: YearMonth,
        accounts: List<Account>,
        creditCards: List<CreditCard>,
        savedPrefs: List<DashboardComponentPreference>?,
    ): DashboardUiState.Editing {
        val effectivePrefs = savedPrefs ?: DashboardComponentRegistry.defaultPreferences()
        val presentKeys = effectivePrefs.map { it.key }.toSet()

        val items = effectivePrefs.sortedBy { it.position }.mapNotNull { pref ->
            val entry = DashboardComponentRegistry.entries.find { it.key == pref.key }
                ?: return@mapNotNull null
            val preview = DashboardComponentVariant.previewForKey(pref.key)
                ?: return@mapNotNull null
            DashboardEditItem(key = pref.key, title = entry.title, config = pref.config, preview = preview)
        }

        val availableItems = DashboardComponentRegistry.entries
            .filter { it.key !in presentKeys }
            .mapNotNull { entry ->
                val preview = DashboardComponentVariant.previewForKey(entry.key)
                    ?: return@mapNotNull null
                DashboardEditItem(key = entry.key, title = entry.title, preview = preview)
            }

        return DashboardUiState.Editing(
            yearMonth = yearMonth,
            items = items,
            availableItems = availableItems,
            accounts = accounts,
            creditCards = creditCards,
        )
    }

    private fun applyPreferences(
        preferences: List<DashboardComponentPreference>,
        all: List<DashboardComponent>,
    ): List<DashboardComponent> {
        val byKey = all.associateBy { it.key }
        return preferences.sortedBy { it.position }.mapNotNull { byKey[it.key] }
    }
}
