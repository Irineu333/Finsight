@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.DashboardComponentPreference
import com.neoutils.finsight.domain.repository.*
import com.neoutils.finsight.domain.usecase.BuildDashboardViewingUseCase
import com.neoutils.finsight.domain.usecase.EnsureDefaultAccountUseCase
import com.neoutils.finsight.domain.usecase.GetDashboardPreferencesUseCase
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
    private val getDashboardPreferences: GetDashboardPreferencesUseCase,
    private val buildDashboardViewingUseCase: BuildDashboardViewingUseCase,
    private val dashboardPreferencesRepository: IDashboardPreferencesRepository,
    private val dashboardPreviewFactory: DashboardPreviewFactory,
) : ViewModel() {

    init {
        viewModelScope.launch {
            ensureDefaultAccountUseCase()
        }
    }

    private val instant get() = Clock.System.now()

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

        val items = buildDashboardViewingUseCase(
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
        initialValue = DashboardUiState.Loading(),
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

        is DashboardAction.MoveComponent -> {
            moveComponent(action.fromKey, action.toKey)
        }

        is DashboardAction.RemoveComponent -> {
            removeComponent(action.key)
        }

        is DashboardAction.UpdateComponentConfig -> {
            updateComponentConfig(action.key, action.config)
        }
    }

    private fun enterEditMode() {
        val current = uiState.value
        viewModelScope.launch {
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
    }

    private fun confirmEdit() = viewModelScope.launch {
        val editing = editingState.value ?: return@launch
        val prefs = editing.items.mapIndexed { i, item ->
            DashboardComponentPreference(
                key = item.key,
                position = i, config = item.config
            )
        }
        dashboardPreferencesRepository.save(prefs)
        editingState.value = null
    }

    private fun moveComponent(fromKey: String, toKey: String) {
        val current = editingState.value ?: return

        val allItems = current.items + current.availableItems
        val fromIndex = allItems.indexOfFirst { it.key == fromKey }.takeIf { it >= 0 } ?: return

        val activeCount = current.items.size

        when (toKey) {
            EDIT_ACTIVE_PLACEHOLDER_KEY -> {
                if (activeCount != 0) return

                val mutable = allItems.toMutableList()
                val moved = mutable.removeAt(fromIndex)
                mutable.add(0, moved)

                editingState.value = current.copy(
                    items = mutable.take(1),
                    availableItems = mutable.drop(1),
                )
            }

            EDIT_SECTION_HEADER_KEY, EDIT_AVAILABLE_PLACEHOLDER_KEY -> {
                val fromInActive = fromIndex < activeCount
                val mutable = allItems.toMutableList()
                val moved = mutable.removeAt(fromIndex)
                if (fromInActive) {
                    val newActiveCount = activeCount - 1
                    mutable.add(newActiveCount, moved)
                    editingState.value = current.copy(
                        items = mutable.take(newActiveCount),
                        availableItems = mutable.drop(newActiveCount),
                    )
                } else {
                    mutable.add(activeCount, moved)
                    val newActiveCount = activeCount + 1
                    editingState.value = current.copy(
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
                editingState.value = current.copy(
                    items = mutable.take(newActiveCount),
                    availableItems = mutable.drop(newActiveCount),
                )
            }
        }
    }

    private fun removeComponent(key: String) {
        val current = editingState.value ?: return
        val item = current.items.find { it.key == key } ?: return

        editingState.value = current.copy(
            items = current.items.filter { it.key != key },
            availableItems = current.availableItems + item,
        )
    }

    private fun updateComponentConfig(
        key: String,
        config: Map<String, String>
    ) {
        val current = editingState.value ?: return

        editingState.value = current.copy(
            items = current.items.map { item ->
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

        val items = preferences.sortedBy {
            it.position
        }.mapNotNull { pref ->
            val preview = dashboardPreviewFactory.createPreview(pref.key) ?: return@mapNotNull null

            DashboardEditItem(
                preview = preview,
                config = pref.config,
            )
        }

        val presentKeys = preferences.map { it.key }.toSet()

        val availableItems = DashboardComponentType.entries
            .filter { it.key !in presentKeys }
            .mapNotNull { entry ->
                val preview = dashboardPreviewFactory.createPreview(entry.key) ?: return@mapNotNull null
                DashboardEditItem(
                    preview = preview,
                    config = preview.config,
                )
            }

        return DashboardUiState.Editing(
            yearMonth = yearMonth,
            items = items,
            availableItems = availableItems,
            accounts = accounts,
            creditCards = creditCards,
        )
    }
}
