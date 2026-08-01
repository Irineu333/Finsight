package com.neoutils.finsight.ui.modal.viewBudget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.exception.DetailNotFoundException
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.domain.usecase.ObserveConsolidationChangesUseCase
import com.neoutils.finsight.extension.interceptAbsence
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.YearMonth

class ViewBudgetViewModel(
    private val budgetId: Long,
    /**
     * The month the screen that opened this was showing. Not "today": a budget's progress
     * is a fact about a month, and reading the current one while the list reads another
     * shows a number that belongs to a different period.
     */
    private val month: YearMonth,
    budgetRepository: IBudgetRepository,
    transactionRepository: ITransactionRepository,
    recurringRepository: IRecurringRepository,
    private val calculateBudgetProgressUseCase: CalculateBudgetProgressUseCase,
    observeConsolidationChanges: ObserveConsolidationChangesUseCase,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val _events = Channel<ViewBudgetEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState = combine(
        budgetRepository.observeAllBudgets(),
        transactionRepository.observeAllTransactions(),
        recurringRepository.observeAllRecurring(),
        // This screen shows the spending **in parts** when a rate is missing, so it is
        // the one place a rate arriving is most visible — and a rate writes no entry,
        // which is the ledger's only trigger. It reached this view model last of the
        // five because it names the reducer only indirectly, through the progress use
        // case, and the guard that pairs the two was looking for the reducer by name.
        observeConsolidationChanges(),
    ) { budgets, transactions, recurringList, _ ->
        calculateBudgetProgressUseCase(
            budgets = budgets,
            recurringList = recurringList,
            transactions = transactions,
            month = month,
        ).firstOrNull { it.budget.id == budgetId }
    }
        .interceptAbsence(
            onMissing = { crashlytics.recordException(DetailNotFoundException("Budget", budgetId)) },
            onDisappeared = { _events.send(ViewBudgetEvent.Dismiss) },
        )
        .map { budgetProgress ->
            budgetProgress?.let { ViewBudgetUiState.Content(it) }
                ?: ViewBudgetUiState.Error
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ViewBudgetUiState.Loading,
        )
}
