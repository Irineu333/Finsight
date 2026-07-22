@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.extension.deriveTransactionType
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateTransactionStatsUseCase
import com.neoutils.finsight.extension.toYearMonth
import com.neoutils.finsight.ui.model.TransactionFacadeLookup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plusMonth
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class TransactionsViewModel(
    private val filterType: TransactionType?,
    private val category: Category?,
    private val filterTarget: TransactionTarget?,
    private val transactionRepository: ITransactionRepository,
    private val categoryRepository: ICategoryRepository,
    private val installmentRepository: IInstallmentRepository,
    private val entryRepository: IEntryRepository,
    private val calculateBalanceUseCase: CalculateBalanceUseCase,
    private val calculateTransactionStatsUseCase: CalculateTransactionStatsUseCase,
) : ViewModel() {

    private val selectedYearMonth = MutableStateFlow(Clock.System.now().toYearMonth())

    private val filters = MutableStateFlow(
        TransactionsFilters(
            category = category,
            type = filterType,
            target = filterTarget,
        )
    )

    val uiState = combine(
        transactionRepository.observeAllTransactions(),
        // The list shows the history of archived categories, so the filter must be
        // able to narrow to one — including closed. This is a filter over existing
        // data, not a selector for a new transaction (which stays open-only).
        categoryRepository.observeAllCategoriesIncludingClosed(),
        installmentRepository.observeAllInstallments(),
        selectedYearMonth,
        filters
    ) { transactions, categories, installments, yearMonth, filters ->
        // Transfers and card payments move money between the user's own accounts;
        // neither is income or expense. Derived from the ledger, never persisted.
        val transactionsForStats = transactions.filterNot {
            it.label == TransactionLabel.TRANSFER || it.label == TransactionLabel.PAYMENT
        }

        val stats = calculateTransactionStatsUseCase(
            transactions = transactionsForStats,
            forYearMonth = yearMonth,
        )

        TransactionsUiState(
            balanceOverview = TransactionsUiState.BalanceOverview(
                income = stats.income,
                expense = stats.expense,
                adjustment = stats.adjustment,
                // Month-wide card payments from the ledger (D12), not summed from the
                // loaded list. Reactive because observeAllTransactions() above re-runs
                // this block on every ledger write, and on month change.
                payment = entryRepository.liabilityMonthFlows(yearMonth).payment,
                // Opening/final balances from the ledger (task 4.11): Σ entries of all
                // ASSET accounts up to the previous / selected month.
                openingBalance = calculateBalanceUseCase(target = yearMonth.minusMonth()),
                finalBalance = calculateBalanceUseCase(target = yearMonth),
            ),
            selectedYearMonth = yearMonth,
            categories = categories,
            // The list still shows a category icon and an installment badge; the
            // ledger only hands out the identities behind them (design D6).
            facadeLookup = TransactionFacadeLookup.of(categories, installments),
            selectedCategory = filters.category,
            selectedType = filters.type,
            selectedTarget = filters.target,
            showRecurringOnly = filters.recurringOnly,
            showInstallmentOnly = filters.installmentOnly,
            transactions = transactions
                .filter(filters.recurringOnly)
                .filterInstallment(filters.installmentOnly)
                .filter(filters.category)
                .filter(filters.type)
                .filter(filters.target)
                .filter { it.date.yearMonth == yearMonth }
                .sortedByDescending { it.date }
                .groupBy { it.date },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransactionsUiState()
    )

    fun onAction(action: TransactionsAction) = viewModelScope.launch {
        when (action) {
            TransactionsAction.NextMonth -> {
                selectedYearMonth.value = selectedYearMonth.value.plusMonth()
            }

            TransactionsAction.PreviousMonth -> {
                selectedYearMonth.value = selectedYearMonth.value.minusMonth()
            }

            is TransactionsAction.SelectMonth -> {
                selectedYearMonth.value = action.yearMonth
            }

            is TransactionsAction.SelectCategory -> {
                filters.value = filters.value.copy(category = action.category)
            }

            is TransactionsAction.SelectType -> {
                filters.value = filters.value.copy(type = action.type)
            }

            is TransactionsAction.SelectTarget -> {
                filters.value = filters.value.copy(target = action.target)
            }

            is TransactionsAction.ToggleRecurring -> {
                filters.value = filters.value.copy(recurringOnly = action.enabled)
            }

            is TransactionsAction.ToggleInstallment -> {
                filters.value = filters.value.copy(installmentOnly = action.enabled)
            }
        }
    }
}

private fun List<Transaction>.filter(recurringOnly: Boolean): List<Transaction> {
    if (!recurringOnly) return this
    return filter { transaction -> transaction.recurringId != null }
}

private fun List<Transaction>.filterInstallment(installmentOnly: Boolean): List<Transaction> {
    if (!installmentOnly) return this
    return filter { transaction -> transaction.installmentId != null }
}

private fun List<Transaction>.filter(category: Category?): List<Transaction> {
    if (category == null) return this
    return filter { it.nominalDimensionId == category.dimensionId }
}

private fun List<Transaction>.filter(type: TransactionType?): List<Transaction> {
    if (type == null) return this
    return filter { transaction ->
        transaction.primaryEntry?.let { deriveTransactionType(it.amount, transaction.entries) } == type
    }
}

private fun List<Transaction>.filter(target: TransactionTarget?): List<Transaction> {
    if (target == null) return this
    return filter { transaction -> transaction.hasLiabilityLeg == target.isCreditCard }
}
