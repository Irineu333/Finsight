@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.viewBudget

import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.usecase.AccountCurrencies
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.domain.usecase.GetAccountCurrenciesUseCase
import com.neoutils.finsight.domain.usecase.ObserveConsolidationChangesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.exception.DetailNotFoundException
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.ContraLeg
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
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.repository.AssetMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.DimensionFlowsByCurrency
import com.neoutils.finsight.domain.repository.LiabilityMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.ScopeStatsByCurrency

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
        override suspend fun hasBudgetForCategory(categoryId: Long) = false
        override suspend fun hasBudgetForRecurring(recurringId: Long) = false
    }

    private class FakeTransactionRepository : ITransactionRepository {
        override fun observeAllTransactions(): Flow<List<Transaction>> = flowOf(emptyList())
        override fun observeTransactionById(id: Long): Flow<Transaction?> = throw NotImplementedError()
        override fun observeTransactionsBy(
            date: LocalDate?,
            dimensionId: Long?,
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
            contra: ContraLeg?,
        ) = throw NotImplementedError()
        override suspend fun deleteTransactionById(id: Long) = throw NotImplementedError()
        override suspend fun deleteTransactionsByIds(ids: List<Long>) = throw NotImplementedError()
    }

    private class FakeRecurringRepository : IRecurringRepository {
        override fun observeAllRecurring(): Flow<List<Recurring>> = flowOf(emptyList())
        override fun observeRecurringById(id: Long): Flow<Recurring?> = throw NotImplementedError()
        override suspend fun getRecurringById(id: Long): Recurring? = null
        override suspend fun hasRecurringForAccount(accountId: Long) = false
        override suspend fun hasRecurringForCreditCard(creditCardId: Long) = false
        override suspend fun hasRecurringForCategory(categoryId: Long) = false
        override suspend fun hasTransactionForRecurring(recurringId: Long) = false
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
        currency = "BRL",
        createdAt = 0L,
    )

    private class FakeEntryRepository : IEntryRepository {
        override suspend fun dimensionBalanceInMonthByCurrency(month: YearMonth, dimensionId: Long) =
            com.neoutils.finsight.domain.model.MoneyByCurrency.of("BRL", 0.0)
        override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
        override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
        override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
        override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows = throw NotImplementedError()
        override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int = throw NotImplementedError()

    override suspend fun accountBalanceUpTo(accountId: Long, target: YearMonth): Double = throw NotImplementedError()
    override suspend fun balanceUpToByCurrency(target: YearMonth): MoneyByCurrency = throw NotImplementedError()
    override suspend fun naturalBalanceUpToByCurrency(target: YearMonth, type: AccountType): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionOwedByCurrency(dimensionId: Long): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionFlowsByCurrency(dimensionId: Long): DimensionFlowsByCurrency = throw NotImplementedError()
    override suspend fun owedByDimensionByCurrency(dimensionIds: Collection<Long>): Map<Long, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun flowsByDimensionByCurrency(dimensionIds: Collection<Long>): Map<Long, DimensionFlowsByCurrency> = throw NotImplementedError()
    override suspend fun liabilityMonthFlowsByCurrency(month: YearMonth): LiabilityMonthFlowsByCurrency = throw NotImplementedError()
    override suspend fun assetMonthFlowsByCurrency(month: YearMonth): AssetMonthFlowsByCurrency = throw NotImplementedError()
    override suspend fun totalsByDimensionByCurrency(
        nominalType: AccountType,
        startDate: LocalDate,
        endDate: LocalDate,
        siblingAccountIds: List<Long>,
    ): Map<Long?, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun totalsByDimensionInScopeByCurrency(
        nominalType: AccountType,
        scopeDimensionIds: List<Long>,
    ): Map<Long?, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun scopeStatsByCurrency(
        scopeAccountIds: List<Long>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): ScopeStatsByCurrency = throw NotImplementedError()
}

    private val MONTH = YearMonth(2026, 3)

    private fun viewModel(
        budgetRepository: FakeBudgetRepository,
        crashlytics: FakeCrashlytics = FakeCrashlytics(),
    ) = ViewBudgetViewModel(
        budgetId = 1L,
        month = MONTH,
        budgetRepository = budgetRepository,
        transactionRepository = FakeTransactionRepository(),
        recurringRepository = FakeRecurringRepository(),
        calculateBudgetProgressUseCase = CalculateBudgetProgressUseCase(FakeEntryRepository(), reducer()),
        observeConsolidationChanges = consolidationChanges(),
        crashlytics = crashlytics,
    )

    /**
     * The trigger, over an archive that never moves.
     *
     * These tests are about which month the progress is read for, not about it
     * recomputing — but the view model listens now, and a `combine` emits nothing until
     * every source has.
     */
    private fun consolidationChanges() = ObserveConsolidationChangesUseCase(
        entryRepository = FakeEntryRepository(),
        baseCurrencyRepository = object : IBaseCurrencyRepository {
            private val flow = MutableStateFlow("BRL")
            override fun observe(): StateFlow<String> = flow
            override suspend fun set(code: String) { flow.value = code }
        },
        exchangeRateRepository = object : IExchangeRateRepository {
            override suspend fun rateAsOf(currency: String, date: LocalDate): ExchangeRate? = null
            override suspend fun ratesAsOf(date: LocalDate) = emptyMap<String, ExchangeRate>()
            override suspend fun rateBetween(from: String, to: String, date: LocalDate): ExchangeRate? = null
            override fun observeAll(): Flow<List<ExchangeRate>> = flowOf(emptyList())
            override suspend fun save(rate: ExchangeRate) = Unit
            override suspend fun remove(rate: ExchangeRate) = Unit
            override suspend fun countNaming(currency: String) = 0
            override suspend fun removeAllNaming(currency: String) = Unit
        },
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

/** The reducer over an archive holding [rates]; the budget's own currency is the target. */
private fun reducer(
    base: String = "BRL",
    rates: Map<String, Double> = emptyMap(),
) = ConsolidateMoneyUseCase(
    baseCurrencyRepository = object : IBaseCurrencyRepository {
        private val flow = MutableStateFlow(base)
        override fun observe(): StateFlow<String> = flow
        override suspend fun set(code: String) { flow.value = code }
    },
    exchangeRateRepository = object : IExchangeRateRepository {
        override suspend fun rateAsOf(currency: String, date: LocalDate) = ratesAsOf(date)[currency]
        override suspend fun ratesAsOf(date: LocalDate) = rates.mapValues { (code, rate) ->
            ExchangeRate(
                currency = code,
                counterCurrency = base,
                date = date,
                rate = rate,
                source = ExchangeRate.Source.USER,
            )
        }

        override suspend fun rateBetween(from: String, to: String, date: LocalDate) =
            ratesAsOf(date)[from]?.takeIf { it.counterCurrency == to }

        override fun observeAll(): Flow<List<ExchangeRate>> = flowOf(emptyList())
        override suspend fun save(rate: ExchangeRate) = Unit
        override suspend fun remove(rate: ExchangeRate) = Unit
        override suspend fun countNaming(currency: String) = 0
        override suspend fun removeAllNaming(currency: String) = Unit
    },
    getAccountCurrencies = object : GetAccountCurrenciesUseCase {
        override suspend fun invoke() = AccountCurrencies(inUse = listOf(base), ofDefaultAccount = base)
    },
)
