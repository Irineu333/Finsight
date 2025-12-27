@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.screen.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finance.domain.usecase.CalculateInvoiceOverviewsUseCase
import com.neoutils.finance.domain.usecase.CalculateInvoiceOverviewsUseCase.CreditCardOverviewResult
import com.neoutils.finance.domain.usecase.CalculateInvoiceOverviewsUseCase.InvoiceOverviewStats
import com.neoutils.finance.domain.usecase.CalculateTransactionStatsUseCase
import com.neoutils.finance.extension.toYearMonth
import com.neoutils.finance.ui.screen.transactions.TransactionsUiState.CreditCardOverview
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plusMonth

class TransactionsViewModel(
    private val transaction: Transaction.Type?,
    private val category: Category?,
    private val target: Transaction.Target?,
    private val transactionRepository: ITransactionRepository,
    private val categoryRepository: ICategoryRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val calculateBalanceUseCase: CalculateBalanceUseCase,
    private val calculateTransactionStatsUseCase: CalculateTransactionStatsUseCase,
    private val calculateInvoiceOverviewsUseCase: CalculateInvoiceOverviewsUseCase
) : ViewModel() {

    private val selectedYearMonth = MutableStateFlow(Clock.System.now().toYearMonth())

    private val filters = MutableStateFlow(
        TransactionsFilters(
            category = category,
            type = transaction,
            target = target
        )
    )

    val uiState = combine(
        transactionRepository.observeAllTransactions(),
        categoryRepository.observeAllCategories(),
        invoiceRepository.observeAllInvoices(),
        selectedYearMonth,
        filters
    ) { transactions, categories, invoices, yearMonth, filters ->
        val stats = calculateTransactionStatsUseCase(
            transactions = transactions,
            forYearMonth = yearMonth,
        )

        val invoiceOverviewStats = calculateInvoiceOverviewsUseCase(
            invoices = invoices,
            transactions = transactions,
            forYearMonth = yearMonth,
        )

        TransactionsUiState(
            transactions = stats.transactions
                .filter(filters.category)
                .filter(filters.type)
                .filter(filters.target)
                .sortedByDescending { it.date }
                .groupBy { it.date },
            balanceOverview = TransactionsUiState.BalanceOverview(
                income = stats.income,
                expense = stats.expense,
                adjustment = stats.adjustment,
                invoicePayment = stats.invoicePayment,
                advancePayment = stats.advancePayment,
                initialBalance = calculateBalanceUseCase(
                    target = yearMonth.minusMonth(),
                    transactions = transactions,
                ),
                finalBalance = calculateBalanceUseCase(
                    target = yearMonth,
                    transactions = transactions,
                )
            ),
            creditCardOverview = invoiceOverviewStats.toUiModel(),
            selectedYearMonth = yearMonth,
            categories = categories,
            selectedCategory = filters.category,
            selectedType = filters.type,
            selectedTarget = filters.target,
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

            is TransactionsAction.SelectCategory -> {
                filters.value = filters.value.copy(category = action.category)
            }

            is TransactionsAction.SelectType -> {
                filters.value = filters.value.copy(type = action.type)
            }

            is TransactionsAction.SelectTarget -> {
                filters.value = filters.value.copy(target = action.target)
            }
        }
    }
}

private fun List<Transaction>.filter(category: Category?): List<Transaction> {
    if (category == null) return this
    return filter { it.category?.id == category.id }
}

private fun List<Transaction>.filter(type: Transaction.Type?): List<Transaction> {
    if (type == null) return this
    return filter { it.type == type }
}

private fun List<Transaction>.filter(target: Transaction.Target?): List<Transaction> {
    if (target == null) return this
    return filter { transaction ->
        when (target) {
            Transaction.Target.ACCOUNT ->
                transaction.target == Transaction.Target.ACCOUNT ||
                        transaction.target == Transaction.Target.INVOICE_PAYMENT

            Transaction.Target.CREDIT_CARD ->
                transaction.target == Transaction.Target.CREDIT_CARD ||
                        transaction.target == Transaction.Target.INVOICE_PAYMENT

            Transaction.Target.INVOICE_PAYMENT ->
                transaction.target == Transaction.Target.INVOICE_PAYMENT
        }
    }
}

private fun InvoiceOverviewStats.toUiModel() = CreditCardOverview(
    expense = creditCardOverview.expense,
    advancePayment = creditCardOverview.advancePayment,
    total = creditCardOverview.total,
    invoices = invoiceOverviews.map {
        TransactionsUiState.InvoiceOverview(
            creditCardName = it.creditCardName,
            invoiceStatus = it.invoiceStatus,
            expense = it.expense,
            advancePayment = it.advancePayment,
            total = it.total,
        )
    }
)
