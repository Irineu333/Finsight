@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.repository.*
import com.neoutils.finsight.domain.usecase.EnsureDefaultAccountUseCase
import com.neoutils.finsight.extension.combine
import com.neoutils.finsight.extension.toYearMonth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class DashboardViewModel(
    private val operationRepository: IOperationRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val accountRepository: IAccountRepository,
    private val budgetRepository: IBudgetRepository,
    private val recurringRepository: IRecurringRepository,
    private val recurringOccurrenceRepository: IRecurringOccurrenceRepository,
    private val ensureDefaultAccountUseCase: EnsureDefaultAccountUseCase,
    private val dashboardComponentsBuilder: DashboardComponentsBuilder,
) : ViewModel() {

    init {
        viewModelScope.launch {
            ensureDefaultAccountUseCase()
        }
    }

    private val instant get() = Clock.System.now()

    private val invoices = invoiceRepository
        .observeUnpaidInvoices()
        .map { invoices ->
            invoices.associateBy { it.creditCard.id }
        }

    val uiState = combine(
        invoices,
        operationRepository.observeAllOperations(),
        creditCardRepository.observeAllCreditCards(),
        accountRepository.observeAllAccounts(),
        budgetRepository.observeAllBudgets(),
        recurringRepository.observeAllRecurring(),
        recurringOccurrenceRepository.observeAllOccurrences(),
    ) { invoices, operations, creditCards, accounts, budgets, recurringList, occurrences ->
        val today = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date

        val components = dashboardComponentsBuilder.build(
            input = DashboardComponentsInput(
                operations = operations,
                creditCards = creditCards,
                invoicesByCreditCardId = invoices,
                accounts = accounts,
                budgets = budgets,
                recurringList = recurringList,
                occurrences = occurrences,
                today = today,
                targetMonth = today.yearMonth,
            ),
        )

        DashboardUiState(
            yearMonth = today.yearMonth,
            components = components,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(yearMonth = instant.toYearMonth()),
    )
}
