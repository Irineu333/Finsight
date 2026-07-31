package com.neoutils.finsight.ui.modal.viewBudget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.exception.DetailNotFoundException
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
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
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val _events = Channel<ViewBudgetEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState = combine(
        budgetRepository.observeAllBudgets(),
        transactionRepository.observeAllTransactions(),
        recurringRepository.observeAllRecurring(),
    ) { budgets, transactions, recurringList ->
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
