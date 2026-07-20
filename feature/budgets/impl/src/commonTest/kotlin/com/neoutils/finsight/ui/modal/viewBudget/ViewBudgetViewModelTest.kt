@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.viewBudget

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.exception.DetailNotFoundException
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import kotlinx.datetime.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ViewBudgetViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class FakeCrashlytics : Crashlytics {
        val recorded = mutableListOf<Throwable>()
        override fun setUserId(id: String?) = Unit
        override fun recordException(e: Throwable) { recorded += e }
    }

    private class FakeBudgetRepository : IBudgetRepository {
        private val all = MutableSharedFlow<List<Budget>>(replay = 1)
        fun emit(budgets: List<Budget>) { all.tryEmit(budgets) }
        override fun observeAllBudgets(): Flow<List<Budget>> = all
        override suspend fun getAllBudgets(): List<Budget> = throw NotImplementedError()
        override suspend fun insert(budget: Budget) = throw NotImplementedError()
        override suspend fun update(budget: Budget) = throw NotImplementedError()
        override suspend fun delete(budget: Budget) = throw NotImplementedError()
    }

    private class FakeTransactionRepository : ITransactionRepository {
        override fun observeAllTransactions(): Flow<List<Transaction>> = flowOf(emptyList())
        override fun observeTransactionById(id: Long): Flow<Transaction?> = throw NotImplementedError()
        override fun observeTransactionsBy(
            date: LocalDate?,
            invoiceId: Long?,
            creditCardId: Long?,
            accountId: Long?,
        ): Flow<List<Transaction>> = throw NotImplementedError()
        override suspend fun getAllTransactions(): List<Transaction> = throw NotImplementedError()
        override suspend fun getTransactionById(id: Long): Transaction? = throw NotImplementedError()
        override suspend fun createTransaction(intent: TransactionIntent): Transaction = throw NotImplementedError()
        override suspend fun createTransactions(intents: List<TransactionIntent>): List<Transaction> = throw NotImplementedError()
        override suspend fun updateTransaction(
            id: Long,
            title: String?,
            date: LocalDate,
            leg: TransactionLeg,
        ) = throw NotImplementedError()
        override suspend fun deleteTransactionById(id: Long) = throw NotImplementedError()
        override suspend fun deleteTransactionsByCreditCard(creditCardId: Long) = throw NotImplementedError()
    }

    private class FakeRecurringRepository : IRecurringRepository {
        override fun observeAllRecurring(): Flow<List<Recurring>> = flowOf(emptyList())
        override fun observeRecurringById(id: Long): Flow<Recurring?> = throw NotImplementedError()
        override suspend fun insert(recurring: Recurring) = throw NotImplementedError()
        override suspend fun update(recurring: Recurring) = throw NotImplementedError()
        override suspend fun delete(recurring: Recurring) = throw NotImplementedError()
    }

    private fun budget(id: Long = 1L, amount: Double = 500.0) = Budget(
        id = id,
        title = "Budget $id",
        categories = emptyList(),
        iconKey = "shopping",
        amount = amount,
        createdAt = 0L,
    )

    private class FakeEntryRepository : IEntryRepository {
        override suspend fun balanceInMonth(month: YearMonth, accountId: Long): Double = 0.0
        override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
        override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
        override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows = throw NotImplementedError()
        override suspend fun entryCountInMonth(month: YearMonth, accountId: Long): Int = throw NotImplementedError()
        override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double = throw NotImplementedError()
        override suspend fun invoiceOwed(invoiceId: Long): Double = throw NotImplementedError()
        override suspend fun invoiceFlows(invoiceId: Long): com.neoutils.finsight.domain.repository.InvoiceFlows = throw NotImplementedError()
        override suspend fun cardMonthFlows(month: YearMonth): com.neoutils.finsight.domain.repository.CardMonthFlows = throw NotImplementedError()
        override suspend fun netWorth(): Double = throw NotImplementedError()
        override suspend fun categoryTotals(
            categoryType: AccountType,
            startDate: kotlinx.datetime.LocalDate,
            endDate: kotlinx.datetime.LocalDate,
            siblingAccountIds: List<Long>,
        ): Map<Long, Double> = throw NotImplementedError()
        override suspend fun categoryTotalsForInvoices(
            categoryType: AccountType,
            invoiceIds: List<Long>,
        ): Map<Long, Double> = throw NotImplementedError()
    }

    private fun viewModel(
        budgetRepository: FakeBudgetRepository,
        crashlytics: FakeCrashlytics = FakeCrashlytics(),
    ) = ViewBudgetViewModel(
        budgetId = 1L,
        budgetRepository = budgetRepository,
        transactionRepository = FakeTransactionRepository(),
        recurringRepository = FakeRecurringRepository(),
        entryRepository = FakeEntryRepository(),
        calculateBudgetProgressUseCase = CalculateBudgetProgressUseCase(),
        crashlytics = crashlytics,
    )

    @Test
    fun loadingThenContent() = runTest(dispatcher) {
        val repository = FakeBudgetRepository()
        val vm = viewModel(repository)

        vm.uiState.test {
            assertEquals(ViewBudgetUiState.Loading, awaitItem())
            repository.emit(listOf(budget(id = 1L, amount = 500.0)))
            assertEquals(500.0, assertIs<ViewBudgetUiState.Content>(awaitItem()).budgetProgress.budget.amount)
        }
    }

    @Test
    fun firstEmissionMissingShowsErrorAndRecordsException() = runTest(dispatcher) {
        val repository = FakeBudgetRepository()
        val crashlytics = FakeCrashlytics()
        val vm = viewModel(repository, crashlytics)

        vm.uiState.test {
            assertEquals(ViewBudgetUiState.Loading, awaitItem())
            repository.emit(emptyList())
            assertEquals(ViewBudgetUiState.Error, awaitItem())
        }

        assertEquals(1, crashlytics.recorded.size)
        assertTrue(crashlytics.recorded.first() is DetailNotFoundException)
    }

    @Test
    fun deletionAfterContentEmitsDismiss() = runTest(dispatcher) {
        val repository = FakeBudgetRepository()
        val vm = viewModel(repository)

        turbineScope {
            val state = vm.uiState.testIn(backgroundScope)
            val events = vm.events.testIn(backgroundScope)

            assertEquals(ViewBudgetUiState.Loading, state.awaitItem())
            repository.emit(listOf(budget(id = 1L)))
            assertIs<ViewBudgetUiState.Content>(state.awaitItem())

            repository.emit(emptyList())
            assertIs<ViewBudgetEvent.Dismiss>(events.awaitItem())
            state.expectNoEvents()

            state.cancel()
            events.cancel()
        }
    }
}
