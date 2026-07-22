@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.repository.dimensionBalancesInMonth
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.extension.toYearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class BudgetsViewModel(
    private val budgetRepository: IBudgetRepository,
    private val transactionRepository: ITransactionRepository,
    private val recurringRepository: IRecurringRepository,
    private val entryRepository: IEntryRepository,
    private val calculateBudgetProgressUseCase: CalculateBudgetProgressUseCase,
) : ViewModel() {

    private val selectedMonth = MutableStateFlow(Clock.System.now().toYearMonth())

    val uiState = combine(
        budgetRepository.observeAllBudgets(),
        transactionRepository.observeAllTransactions(),
        recurringRepository.observeAllRecurring(),
        selectedMonth,
    ) { budgets, transactions, recurringList, selectedMonth ->
        val categoryBalances = entryRepository.dimensionBalancesInMonth(
            month = selectedMonth,
            dimensionIds = budgets.flatMap { budget -> budget.categories.map { it.dimensionId } },
        )
        val budgetProgress = calculateBudgetProgressUseCase(
            budgets = budgets,
            categoryBalances = categoryBalances,
            recurringList = recurringList,
            transactions = transactions,
            month = selectedMonth,
        )
        if (budgetProgress.isEmpty()) {
            BudgetsUiState.Empty(selectedMonth = selectedMonth)
        } else {
            BudgetsUiState.Content(
                budgetProgress = budgetProgress,
                selectedMonth = selectedMonth,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BudgetsUiState.Loading(selectedMonth = selectedMonth.value),
    )

    fun onAction(action: BudgetsAction) {
        when (action) {
            is BudgetsAction.SelectMonth -> selectedMonth.update { action.yearMonth }
        }
    }
}
