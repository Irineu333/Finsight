@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.modal.launchYield

import androidx.compose.runtime.Composable
import app.cash.turbine.test
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.Event
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.usecase.EnsureYieldCategoryUseCase
import com.neoutils.finsight.domain.usecase.LaunchYieldUseCase
import com.neoutils.finsight.domain.usecase.YieldCategoryStore
import com.neoutils.finsight.ui.component.Modal
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LaunchYieldViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val account = Account(id = 1, name = "Nubank", type = AccountType.ASSET, currency = "BRL", yieldsInterest = true)
    private val picpay = Account(id = 2, name = "PicPay", type = AccountType.ASSET, currency = "BRL", yieldsInterest = true)
    private val wallet = Account(id = 3, name = "Carteira", type = AccountType.ASSET, currency = "BRL")

    private fun viewModel(
        transactions: ITransactionRepository,
        manager: ModalManager,
        analytics: FakeAnalytics,
        crashlytics: FakeCrashlytics,
        accounts: List<Account> = listOf(account, picpay, wallet),
    ) = LaunchYieldViewModel(
        account = account,
        launchYieldUseCase = LaunchYieldUseCase(transactions, EnsureYieldCategoryUseCase(YieldCategoryStore())),
        accountRepository = AccountStore(accounts),
        modalManager = manager,
        analytics = analytics,
        crashlytics = crashlytics,
    )

    @Test
    fun `the sheet loads and settles on the account with today's date`() = runTest(dispatcher) {
        val viewModel = viewModel(RecordingTransactions(), ModalManager(), FakeAnalytics(), FakeCrashlytics())

        viewModel.uiState.test {
            assertEquals(LaunchYieldUiState.Loading, awaitItem())

            val state = assertIs<LaunchYieldUiState.Content>(awaitItem())
            assertEquals(account.id, state.account.id)
            assertFalse(state.isSubmitting)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `only the accounts that declare a yield are offered`() = runTest(dispatcher) {
        val viewModel = viewModel(RecordingTransactions(), ModalManager(), FakeAnalytics(), FakeCrashlytics())

        viewModel.uiState.test {
            awaitItem()

            // "Carteira" declares none, so the selector must not offer it — the sheet
            // would otherwise reach an account the card refuses to reach it from.
            val state = assertIs<LaunchYieldUiState.Content>(awaitItem())
            assertEquals(listOf(account.id, picpay.id), state.accounts.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the launch lands on the account picked in the selector`() = runTest(dispatcher) {
        val transactions = RecordingTransactions()
        val manager = ModalManager()
        val viewModel = viewModel(transactions, manager, FakeAnalytics(), FakeCrashlytics())

        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()

            viewModel.onAction(LaunchYieldAction.SelectAccount(picpay))
            viewModel.onAction(LaunchYieldAction.Submit(12.40))
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(picpay.id, transactions.created.single().legs.single().accountId)
    }

    @Test
    fun `choosing a date changes only the date`() = runTest(dispatcher) {
        val viewModel = viewModel(RecordingTransactions(), ModalManager(), FakeAnalytics(), FakeCrashlytics())
        val chosen = LocalDate(2026, 7, 1)

        viewModel.uiState.test {
            assertEquals(LaunchYieldUiState.Loading, awaitItem())
            awaitItem()

            viewModel.onAction(LaunchYieldAction.DateChanged(chosen))

            val state = assertIs<LaunchYieldUiState.Content>(awaitItem())
            assertEquals(chosen, state.date)
            assertEquals(account.id, state.account.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a successful launch writes the transaction and closes the sheet`() = runTest(dispatcher) {
        val transactions = RecordingTransactions()
        val manager = ModalManager()
        val analytics = FakeAnalytics()
        val crashlytics = FakeCrashlytics()
        val modal = FakeModal().also(manager::show)
        val viewModel = viewModel(transactions, manager, analytics, crashlytics)

        viewModel.uiState.test {
            awaitItem()
            advanceUntilIdle()

            viewModel.onAction(LaunchYieldAction.DateChanged(LocalDate(2026, 7, 1)))
            viewModel.onAction(LaunchYieldAction.Submit(12.40))
            advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, transactions.created.size)
        assertEquals(LocalDate(2026, 7, 1), transactions.created.single().date)
        assertEquals(12.40, transactions.created.single().legs.single().amount)
        assertEquals(listOf("launch_yield"), analytics.events.map { it.name })
        assertTrue(modal.dismissed)
        assertTrue(crashlytics.recorded.isEmpty())
    }

    @Test
    fun `a failure is recorded and the sheet stays open`() = runTest(dispatcher) {
        val failure = IllegalStateException("write failed")
        val transactions = RecordingTransactions(failure = failure)
        val manager = ModalManager()
        val analytics = FakeAnalytics()
        val crashlytics = FakeCrashlytics()
        val modal = FakeModal().also(manager::show)
        val viewModel = viewModel(transactions, manager, analytics, crashlytics)
        advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem()

            viewModel.onAction(LaunchYieldAction.Submit(12.40))
            advanceUntilIdle()

            assertEquals(listOf<Throwable>(failure), crashlytics.recorded)
            assertTrue(analytics.events.isEmpty())
            // A closed sheet would read as success — the refusal has to stay visible.
            assertFalse(modal.dismissed)
            // And the form goes back to accepting input rather than staying stuck.
            assertFalse(assertIs<LaunchYieldUiState.Content>(expectMostRecentItem()).isSubmitting)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class FakeAnalytics : Analytics {
        val events = mutableListOf<Event>()
        override fun logScreenView(screenName: String) = Unit
        override fun logEvent(event: Event) { events += event }
        override fun setUserId(id: String?) = Unit
    }

    private class FakeCrashlytics : Crashlytics {
        val recorded = mutableListOf<Throwable>()
        override fun setUserId(id: String?) = Unit
        override fun recordException(e: Throwable) { recorded += e }
    }

    /** Standing in for the sheet, so dismissal is observable from outside. */
    private class FakeModal : Modal() {
        var dismissed = false
        override fun onDismissed() { dismissed = true }

        @Composable
        override fun Content() = Unit
    }

    private class RecordingTransactions(
        private val failure: Throwable? = null,
    ) : ITransactionRepository {

        val created = mutableListOf<TransactionIntent>()

        override suspend fun createTransaction(intent: TransactionIntent): Transaction {
            failure?.let { throw it }
            created += intent
            return Transaction(id = created.size.toLong(), title = intent.title, date = intent.date, entries = emptyList())
        }

        override suspend fun createTransactions(intents: List<TransactionIntent>) = intents.map { createTransaction(it) }
        override suspend fun updateTransaction(
            id: Long,
            title: String?,
            date: LocalDate,
            legs: List<TransactionLeg>,
            contra: ContraLeg?,
        ) = throw NotImplementedError()
        override suspend fun deleteTransactionById(id: Long) = throw NotImplementedError()
        override suspend fun deleteTransactionsByIds(ids: List<Long>) = throw NotImplementedError()
        override fun observeTransactionsBy(date: LocalDate?, dimensionId: Long?, accountId: Long?): Flow<List<Transaction>> =
            flowOf(emptyList())
        override fun observeAllTransactions(): Flow<List<Transaction>> = throw NotImplementedError()
        override fun observeTransactionById(id: Long): Flow<Transaction?> = throw NotImplementedError()
        override suspend fun getAllTransactions(): List<Transaction> = throw NotImplementedError()
        override suspend fun getTransactionById(id: Long): Transaction? = throw NotImplementedError()
    }

    private class AccountStore(private val accounts: List<Account>) : IAccountRepository {
        override suspend fun getAccountById(accountId: Long): Account? = accounts.firstOrNull { it.id == accountId }
        override suspend fun hasYieldingAccount(): Boolean = accounts.any { it.yieldsInterest }
        override fun observeAllAccounts(): Flow<List<Account>> = flowOf(accounts)
        override suspend fun getAllAccounts(): List<Account> = accounts
        override suspend fun getAllAccountsIncludingClosed(): List<Account> = accounts
        override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = flowOf(accounts)
        override suspend fun getAllLedgerAccounts(): List<Account> = accounts
        override fun observeAllLedgerAccounts(): Flow<List<Account>> = flowOf(accounts)
        override fun observeAccountById(accountId: Long): Flow<Account?> = flowOf(accounts.firstOrNull())
        override suspend fun getDefaultAccount(): Account? = null
        override fun observeDefaultAccount(): Flow<Account?> = flowOf(null)
        override suspend fun getAccountCount(): Int = accounts.size
        override suspend fun insert(account: Account): Long = throw NotImplementedError()
        override suspend fun update(account: Account) = throw NotImplementedError()
        override suspend fun delete(account: Account) = throw NotImplementedError()
        override suspend fun reopen(accountId: Long) = throw NotImplementedError()
    }
}
