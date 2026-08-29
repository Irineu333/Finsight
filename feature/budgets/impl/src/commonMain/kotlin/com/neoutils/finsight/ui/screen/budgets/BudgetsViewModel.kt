@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.BudgetProgress
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.domain.usecase.ObserveConsolidationChangesUseCase
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
    private val calculateBudgetProgressUseCase: CalculateBudgetProgressUseCase,
    private val observeConsolidationChanges: ObserveConsolidationChangesUseCase,
) : ViewModel() {

    private val selectedMonth = MutableStateFlow(Clock.System.now().toYearMonth())

    val uiState = combine(
        budgetRepository.observeAllBudgets(),
        transactionRepository.observeAllTransactions(),
        recurringRepository.observeAllRecurring(),
        selectedMonth,
        // The spending behind each bar is an SQL aggregate reduced to the limit's own
        // currency, so it moves when the ledger moves *and* when a rate does — and a
        // rate writes no entry. Without this the bar keeps whatever it last computed.
        observeConsolidationChanges(),
    ) { budgets, transactions, recurringList, selectedMonth, _ ->
        val budgetProgress = calculateBudgetProgressUseCase(
            budgets = budgets,
            recurringList = recurringList,
            transactions = transactions,
            month = selectedMonth,
        ).sortedByConsumption()
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

/**
 * **The list, in the order the screen is read: most of the ceiling consumed first.**
 *
 * The order of creation answers no question this screen asks, and it makes an exceeded
 * budget turn up wherever it happens to have been created. Sorting by consumption puts
 * what needs acting on at the top for zero dp of heading, and turns the progress gradient
 * into one continuous scale from the top of the list down — which is also the deliberate
 * compensation for the ring: an arc compares worse between rows than a bar does, so the
 * list arrives **pre-compared** instead.
 *
 * **It cannot come from `BudgetDao`.** Consumption is not a property of the stored budget:
 * it is a reading of the ledger reduced to the limit's currency, so it exists only after
 * `CalculateBudgetProgressUseCase` has run.
 *
 * A budget whose consumption is unknown goes to the **end**, never to the front: ordered
 * as a zero it would sit at the top of the list among the untouched ones, which is to
 * place what nothing is known about beside what is known to be fine.
 *
 * The sort is stable, so budgets that consumed the same share keep the order they were
 * created in — including two that both saturated the ring.
 */
internal fun List<BudgetProgress>.sortedByConsumption(): List<BudgetProgress> = sortedWith(
    compareByDescending<BudgetProgress> { it.consumedShare != null }
        .thenByDescending { it.consumedShare ?: 0.0 }
)

/**
 * The share of the ceiling already spent — `null` when there is no share to take.
 *
 * It is deliberately **not** [BudgetProgress.progress]: that one is how full the ring is
 * drawn, clamped to the turn it can make, and clamping makes 100% and 300% the same
 * number. The order is about which budget needs attention first, so it reads the ratio
 * itself and a threefold overrun outranks a budget that has just gone over.
 *
 * `null` covers both ways the share fails to exist: spending that no rate could reduce to
 * the limit's currency, and a ceiling of zero with nothing spent against it. A ceiling of
 * zero with something spent against it is `Infinity`, which is the right answer and sorts
 * where it belongs.
 */
private val BudgetProgress.consumedShare: Double?
    get() = (spent / budget.amount).takeIf { isResolved && !it.isNaN() }
