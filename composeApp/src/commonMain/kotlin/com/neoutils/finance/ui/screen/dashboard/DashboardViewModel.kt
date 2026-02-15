@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.signedImpact
import com.neoutils.finance.domain.repository.IAccountRepository
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.repository.IOperationRepository
import com.neoutils.finance.extension.toYearMonth
import com.neoutils.finance.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finance.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finance.domain.usecase.CalculateTransactionStatsUseCase
import com.neoutils.finance.domain.usecase.EnsureDefaultAccountUseCase
import com.neoutils.finance.ui.mapper.InvoiceUiMapper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class DashboardViewModel(
    private val operationRepository: IOperationRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val accountRepository: IAccountRepository,
    private val calculateBalanceUseCase: CalculateBalanceUseCase,
    private val calculateTransactionStatsUseCase: CalculateTransactionStatsUseCase,
    private val calculateCategorySpendingUseCase: CalculateCategorySpendingUseCase,
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
    ) { operations, creditCards, invoices, accounts ->
        val transactions = operations.flatMap { it.transactions }

        val stats = calculateTransactionStatsUseCase(
            transactions = transactions,
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
                balance = calculateBalanceUseCase(
                    target = currentMonth,
                    transactions = transactions,
                )
            ),
            yearMonth = currentMonth,
            categorySpending = categorySpending,
            creditCards = creditCardsWithBills,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(),
    )
}