@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.extension.toYearMonth
import com.neoutils.finance.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finance.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finance.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finance.domain.usecase.CalculateTransactionStatsUseCase
import com.neoutils.finance.ui.mapper.CreditCardBillUiMapper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class DashboardViewModel(
    private val transactionRepository: ITransactionRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val calculateBalanceUseCase: CalculateBalanceUseCase,
    private val calculateTransactionStatsUseCase: CalculateTransactionStatsUseCase,
    private val calculateCategorySpendingUseCase: CalculateCategorySpendingUseCase,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
    private val creditCardBillUiMapper: CreditCardBillUiMapper,
) : ViewModel() {

    private val instant get() = Clock.System.now()
    private val currentMonth get() = instant.toYearMonth()

    val uiState = combine(
        transactionRepository.observeAllTransactions(),
        creditCardRepository.observeAllCreditCards(),
        invoiceRepository.observeAllInvoices()
    ) { transactions, creditCards, _ ->

        val stats = calculateTransactionStatsUseCase(
            transactions = transactions,
            forYearMonth = currentMonth
        )

        val categorySpending = calculateCategorySpendingUseCase(
            transactions = transactions,
            forYearMonth = currentMonth
        )

        val creditCardsWithBills = creditCards.map { creditCard ->
            val invoice = invoiceRepository.getLatestUnpaidInvoice(creditCard.id)
            val billAmount = invoice?.let {
                calculateInvoiceUseCase(
                    invoiceId = it.id,
                    transactions = transactions
                )
            } ?: 0.0

            CreditCardWithBill(
                creditCard = creditCard,
                billUi = creditCardBillUiMapper.toUi(
                    bill = billAmount,
                    limit = creditCard.limit,
                    invoiceStatus = invoice?.status
                ),
                billAmount = billAmount,
                currentInvoice = invoice
            )
        }

        DashboardUiState(
            recents = stats.transactions.take(3),
            balance = DashboardUiState.BalanceStats(
                income = stats.income,
                expense = stats.expense,
                balance = calculateBalanceUseCase(
                    target = currentMonth,
                    transactions = transactions,
                )
            ),
            yearMonth = currentMonth,
            categorySpending = categorySpending.take(3),
            creditCards = creditCardsWithBills.take(3),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(),
    )
}