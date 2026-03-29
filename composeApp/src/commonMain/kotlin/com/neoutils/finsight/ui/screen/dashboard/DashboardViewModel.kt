@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.DashboardComponentPreference
import com.neoutils.finsight.domain.repository.*
import com.neoutils.finsight.domain.usecase.EnsureDefaultAccountUseCase
import com.neoutils.finsight.extension.combine
import com.neoutils.finsight.extension.toYearMonth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
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

    private val _editingState = MutableStateFlow<DashboardUiState.Editing?>(null)

    private val viewingState: Flow<DashboardUiState> = combine(
        invoices,
        operationRepository.observeAllOperations(),
        creditCardRepository.observeAllCreditCards(),
        accountRepository.observeAllAccounts(),
        budgetRepository.observeAllBudgets(),
        recurringRepository.observeAllRecurring(),
        recurringOccurrenceRepository.observeAllOccurrences(),
        dashboardPreferencesRepository.observe(),
    ) { invoices, operations, creditCards, accounts, budgets, recurringList, occurrences, preferences ->
        val today = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date

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
            ),
        )

        val ordered = applyPreferences(preferences, allComponents)

        DashboardUiState.Viewing(
            yearMonth = today.yearMonth,
            components = ordered,
        )
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
        is DashboardAction.MoveComponent -> moveComponent(action.from, action.to)
        is DashboardAction.RemoveComponent -> removeComponent(action.key)
        is DashboardAction.AddComponent -> addComponent(action.key, action.insertAt)
        is DashboardAction.UpdateComponentConfig -> updateComponentConfig(action.key, action.config)
        is DashboardAction.AdjustBalance -> Unit
    }

    private fun enterEditMode() {
        viewModelScope.launch {
            val current = uiState.value as? DashboardUiState.Viewing ?: return@launch
            val savedPrefs = dashboardPreferencesRepository.observe().first()
            _editingState.value = buildEditingState(current, savedPrefs)
        }
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

    private fun moveComponent(from: Int, to: Int) {
        val current = _editingState.value ?: return
        val items = current.items.toMutableList()
        items.add(to, items.removeAt(from))
        _editingState.value = current.copy(items = items)
    }

    private fun removeComponent(key: String) {
        val current = _editingState.value ?: return
        val removed = current.items.find { it.key == key } ?: return
        _editingState.value = current.copy(
            items = current.items.filter { it.key != key },
            availableItems = current.availableItems + removed,
        )
    }

    private fun addComponent(key: String, insertAt: Int?) {
        val current = _editingState.value ?: return
        val added = current.availableItems.find { it.key == key } ?: return
        val newItems = current.items.toMutableList().also { list ->
            if (insertAt != null) list.add(insertAt.coerceIn(0, list.size), added)
            else list.add(added)
        }
        _editingState.value = current.copy(
            items = newItems,
            availableItems = current.availableItems.filter { it.key != key },
        )
    }

    private fun updateComponentConfig(key: String, config: Map<String, String>) {
        val current = _editingState.value ?: return
        _editingState.value = current.copy(
            items = current.items.map { if (it.key == key) it.copy(config = config) else it },
        )
    }

    private fun buildEditingState(
        viewing: DashboardUiState.Viewing,
        savedPrefs: List<DashboardComponentPreference>,
    ): DashboardUiState.Editing {
        val items: List<DashboardEditItem>
        val availableItems: List<DashboardEditItem>

        if (savedPrefs.isEmpty()) {
            items = DashboardComponentRegistry.entries.mapNotNull { entry ->
                val preview = DashboardComponentVariant.previewForKey(entry.key) ?: return@mapNotNull null
                DashboardEditItem(key = entry.key, title = entry.title, preview = preview)
            }
            availableItems = emptyList()
        } else {
            val presentKeys = savedPrefs.map { it.key }.toSet()
            items = savedPrefs.sortedBy { it.position }.mapNotNull { pref ->
                val entry = DashboardComponentRegistry.entries.find { it.key == pref.key }
                    ?: return@mapNotNull null
                val preview = DashboardComponentVariant.previewForKey(pref.key) ?: return@mapNotNull null
                DashboardEditItem(key = pref.key, title = entry.title, config = pref.config, preview = preview)
            }
            availableItems = DashboardComponentRegistry.entries
                .filter { it.key !in presentKeys }
                .mapNotNull { entry ->
                    val preview = DashboardComponentVariant.previewForKey(entry.key) ?: return@mapNotNull null
                    DashboardEditItem(key = entry.key, title = entry.title, preview = preview)
                }
        }

        return DashboardUiState.Editing(
            yearMonth = viewing.yearMonth,
            items = items,
            availableItems = availableItems,
        )
    }

    private fun applyPreferences(
        preferences: List<DashboardComponentPreference>,
        all: List<DashboardComponent>,
    ): List<DashboardComponent> {
        if (preferences.isEmpty()) return all
        val byKey = all.associateBy { it.key }
        return preferences.sortedBy { it.position }.mapNotNull { byKey[it.key] }
    }
}
