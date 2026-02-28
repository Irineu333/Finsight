@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.signedImpact
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.extension.toYearMonth
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finsight.domain.usecase.CalculateTransactionStatsUseCase
import com.neoutils.finsight.domain.usecase.EnsureDefaultAccountUseCase
import com.neoutils.finsight.ui.mapper.InvoiceUiMapper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class DashboardViewModel(
    private val operationRepository: IOperationRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val accountRepository: IAccountRepository,
    private val budgetRepository: IBudgetRepository,
    private val calculateBalanceUseCase: CalculateBalanceUseCase,
    private val calculateTransactionStatsUseCase: CalculateTransactionStatsUseCase,
    private val calculateCategorySpendingUseCase: CalculateCategorySpendingUseCase,
    private val calculateBudgetProgressUseCase: CalculateBudgetProgressUseCase,
    private val ensureDefaultAccountUseCase: EnsureDefaultAccountUseCase,
    private val invoiceUiMapper: InvoiceUiMapper,
) : ViewModel() {

    init {
        viewModelScope.launch {
            ensureDefaultAccountUseCase()
        }
    }

    private val instant get() = Clock.System.now()
    private val currentMonth get() = instant.toYearMonth()

    private val invoicesFlow = invoiceRepository
        .observeUnpaidInvoices()
        .map { invoices ->
            invoices.associateBy { it.creditCard.id }
        }

    val uiState = combine(
        operationRepository.observeAllOperations(),
        creditCardRepository.observeAllCreditCards(),
        invoicesFlow,
        accountRepository.observeAllAccounts(),
        budgetRepository.observeAllBudgets(),
    ) { operations, creditCards, invoices, accounts, budgets ->
        val transactions = operations.flatMap { it.transactions }
        val transactionsForStats = operations
            .filterNot { it.kind == Operation.Kind.TRANSFER || it.kind == Operation.Kind.PAYMENT }
            .flatMap { it.transactions }

        val stats = calculateTransactionStatsUseCase(
            transactions = transactionsForStats,
            forYearMonth = currentMonth
        )

        val categorySpending = calculateCategorySpendingUseCase(
            transactions = transactions,
            forYearMonth = currentMonth
        )

        val creditCardsWithBills = creditCards.map { creditCard ->
            val invoice = invoices[creditCard.id]

            CreditCardUi(
                creditCard = creditCard,
                invoiceUi = invoice?.let {
                    invoiceUiMapper.toUi(
                        invoice = it,
                    )
                },
            )
        }

        val accountsUi = accounts.map { account ->
            val accountTransactions = transactions.filter { it.account?.id == account.id }
            val balance = accountTransactions.sumOf { it.signedImpact() }
            DashboardAccountUi(
                account = account,
                balance = balance,
            )
        }

        DashboardUiState(
            accounts = accountsUi,
            recents = operations.sortedByDescending { it.date }.take(4),
            hasMoreRecents = operations.size > 3,
            balance = DashboardUiState.BalanceStats(
                income = stats.income,
                expense = stats.expense,
                payment = operations
                    .filter { it.kind == Operation.Kind.PAYMENT }
                    .filter { it.date.yearMonth == currentMonth }
                    .sumOf { it.amount },
                balance = calculateBalanceUseCase(
                    target = currentMonth,
                    transactions = transactions,
                )
            ),
            yearMonth = currentMonth,
            categorySpending = categorySpending,
            creditCards = creditCardsWithBills,
            budgetProgress = calculateBudgetProgressUseCase(
                budgets = budgets,
                transactions = transactions,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(),
    )
}