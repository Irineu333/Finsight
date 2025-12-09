@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.data.repository.PreferencesRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.extension.toYearMonth
import com.neoutils.finance.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finance.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finance.domain.usecase.CalculateCreditCardBillUseCase
import com.neoutils.finance.domain.usecase.CalculateTransactionStatsUseCase
import com.neoutils.finance.ui.mapper.CreditCardBillUiMapper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class DashboardViewModel(
    private val repository: ITransactionRepository,
    private val preferencesRepository: PreferencesRepository,
    private val calculateBalanceUseCase: CalculateBalanceUseCase,
    private val calculateTransactionStatsUseCase: CalculateTransactionStatsUseCase,
    private val calculateCategorySpendingUseCase: CalculateCategorySpendingUseCase,
    private val calculateCreditCardBillUseCase: CalculateCreditCardBillUseCase,
    private val creditCardBillUiMapper: CreditCardBillUiMapper,
) : ViewModel() {

    private val instant get() = Clock.System.now()
    private val currentMonth get() = instant.toYearMonth()

    val uiState = combine(
        repository.observeAllTransactions(),
        preferencesRepository.observeCreditCardLimit()
    ) { transactions, creditCardLimit ->

        val stats = calculateTransactionStatsUseCase(
            transactions = transactions,
            forYearMonth = currentMonth
        )

        val categorySpending = calculateCategorySpendingUseCase(
            transactions = transactions,
            forYearMonth = currentMonth
        )

        val invoiceAmount = calculateCreditCardBillUseCase(
            target = currentMonth,
            transactions = transactions
        )

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
            creditCardBill = creditCardBillUiMapper.toUi(
                bill = invoiceAmount,
                limit = creditCardLimit
            ),
            creditCardBillAmount = invoiceAmount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(),
    )
}