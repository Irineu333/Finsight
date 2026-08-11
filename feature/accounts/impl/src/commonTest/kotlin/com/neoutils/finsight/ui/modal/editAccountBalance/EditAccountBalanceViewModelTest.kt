@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.editAccountBalance

import app.cash.turbine.test
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.Event
import com.neoutils.finsight.domain.closingBalanceDateOf
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.openingBalanceDateOf
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.usecase.AdjustBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.FakeEntryRepository
import com.neoutils.finsight.domain.usecase.FakeTransactionRepository
import com.neoutils.finsight.domain.usecase.LedgerStore
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * One adjustment, dated. The two entry points differ only by the date they open on, and the
 * reference value follows that date — which is what makes the displayed difference equal to
 * the written one, in every entry point.
 *
 * Today is 11 August 2026. The account moved twice: +100 in February and +40 in April.
 */
class EditAccountBalanceViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val today = LocalDate(2026, 8, 11)
    private val march = YearMonth(2026, 3)

    private val account = Account(
        id = 1,
        name = "Checking",
        type = AccountType.ASSET,
        currency = "BRL",
    )

    private fun seededLedger() = LedgerStore(account).apply {
        seedMovement(date = LocalDate(2026, 2, 20), cents = 10_000)
        seedMovement(date = LocalDate(2026, 4, 5), cents = 4_000)
    }

    @Test
    fun `the opening shortcut opens on the end of the previous month and reads the balance there`() =
        runTest(dispatcher) {
            val ledger = seededLedger()
            val viewModel = viewModel(ledger, openingBalanceDateOf(march, today))

            viewModel.uiState.test {
                advanceUntilIdle()
                val state = expectMostRecentItem() as EditAccountBalanceUiState.Content

                assertEquals("28/02/2026", state.date)
                // The divergence this change exists to kill: 100, the balance on 28
                // February — not 100 plus April's 40, and not the end of March read
                // through a target month of its own.
                assertEquals(100.0, state.currentBalance)
            }
        }

    @Test
    fun `the closing shortcut of a past month opens on that month's last day`() = runTest(dispatcher) {
        val ledger = seededLedger()
        val viewModel = viewModel(ledger, closingBalanceDateOf(march, today))

        viewModel.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem() as EditAccountBalanceUiState.Content

            assertEquals("31/03/2026", state.date)
            assertEquals(100.0, state.currentBalance)
        }
    }

    @Test
    fun `the closing shortcut of the current month opens on today`() = runTest(dispatcher) {
        val ledger = seededLedger()
        val viewModel = viewModel(ledger, closingBalanceDateOf(YearMonth(2026, 8), today))

        viewModel.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem() as EditAccountBalanceUiState.Content

            assertEquals("11/08/2026", state.date)
            assertEquals(140.0, state.currentBalance)
        }
    }

    @Test
    fun `moving the date moves the reference value`() = runTest(dispatcher) {
        val ledger = seededLedger()
        val viewModel = viewModel(ledger, openingBalanceDateOf(march, today))

        viewModel.uiState.test {
            advanceUntilIdle()
            assertEquals(
                100.0,
                (expectMostRecentItem() as EditAccountBalanceUiState.Content).currentBalance,
            )

            viewModel.onAction(EditAccountBalanceAction.ChangeDate("30/04/2026"))
            advanceUntilIdle()

            val state = expectMostRecentItem() as EditAccountBalanceUiState.Content
            assertEquals("30/04/2026", state.date)
            assertEquals(140.0, state.currentBalance)
        }
    }

    /**
     * The difference the form shows is `target - currentBalance`, and the ledger receives
     * exactly that. Reading the reference value on the date is what makes the two agree.
     */
    @Test
    fun `the displayed difference is the difference written to the ledger`() = runTest(dispatcher) {
        val ledger = seededLedger()
        val viewModel = viewModel(ledger, openingBalanceDateOf(march, today))

        viewModel.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem() as EditAccountBalanceUiState.Content
            val displayedDifference = 175.0 - state.currentBalance

            viewModel.onAction(EditAccountBalanceAction.Submit(targetBalance = 175.0))
            advanceUntilIdle()

            assertEquals(75.0, displayedDifference)
            assertEquals(
                mapOf(LocalDate(2026, 2, 28) to 75.0),
                ledger.adjustmentsByDate(),
            )
        }
    }

    /**
     * No entry point reaches anything another could not: the shortcut is a date, and
     * nothing else.
     */
    @Test
    fun `opening by one shortcut and moving to the other's date gives the same entries`() =
        runTest(dispatcher) {
            val viaShortcut = seededLedger()
            viewModel(viaShortcut, closingBalanceDateOf(march, today)).let { viewModel ->
                advanceUntilIdle()
                viewModel.onAction(EditAccountBalanceAction.Submit(targetBalance = 175.0))
                advanceUntilIdle()
            }

            val viaDate = seededLedger()
            viewModel(viaDate, openingBalanceDateOf(march, today)).let { viewModel ->
                advanceUntilIdle()
                viewModel.onAction(EditAccountBalanceAction.ChangeDate("31/03/2026"))
                advanceUntilIdle()
                viewModel.onAction(EditAccountBalanceAction.Submit(targetBalance = 175.0))
                advanceUntilIdle()
            }

            assertEquals(viaShortcut.adjustmentsByDate(), viaDate.adjustmentsByDate())
            assertEquals(mapOf(LocalDate(2026, 3, 31) to 75.0), viaDate.adjustmentsByDate())
        }

    private fun viewModel(ledger: LedgerStore, initialDate: LocalDate) = EditAccountBalanceViewModel(
        initialDate = initialDate,
        account = account,
        adjustBalanceUseCase = AdjustBalanceUseCase(
            transactionRepository = FakeTransactionRepository(ledger),
            calculateBalanceUseCase = CalculateBalanceUseCase(FakeEntryRepository(ledger)),
        ),
        calculateBalanceUseCase = CalculateBalanceUseCase(FakeEntryRepository(ledger)),
        accountRepository = OneAccount(account),
        modalManager = ModalManager(),
        analytics = MuteAnalytics,
        crashlytics = MuteCrashlytics,
        clock = ClockOn(today),
    )
}

private class ClockOn(private val today: LocalDate) : Clock {
    override fun now(): Instant = today.atStartOfDayIn(TimeZone.currentSystemDefault())
}

private object MuteAnalytics : Analytics {
    override fun logScreenView(screenName: String) = Unit
    override fun logEvent(event: Event) = Unit
    override fun setUserId(id: String?) = Unit
}

private object MuteCrashlytics : Crashlytics {
    override fun setUserId(id: String?) = Unit
    override fun recordException(e: Throwable) = Unit
}

private class OneAccount(private val account: Account) : IAccountRepository {
    override suspend fun getAllAccounts(): List<Account> = listOf(account)
    override suspend fun getAccountById(accountId: Long): Account? = account.takeIf { it.id == accountId }
    override fun observeAllAccounts(): Flow<List<Account>> = flowOf(listOf(account))
    override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = throw NotImplementedError()
    override fun observeAllLedgerAccounts(): Flow<List<Account>> = throw NotImplementedError()
    override fun observeAccountById(accountId: Long): Flow<Account?> = throw NotImplementedError()
    override fun observeDefaultAccount(): Flow<Account?> = throw NotImplementedError()
    override suspend fun getAllAccountsIncludingClosed(): List<Account> = throw NotImplementedError()
    override suspend fun getAllLedgerAccounts(): List<Account> = throw NotImplementedError()
    override suspend fun getDefaultAccount(): Account? = throw NotImplementedError()
    override suspend fun getAccountCount(): Int = throw NotImplementedError()
    override suspend fun hasYieldingAccount(): Boolean = throw NotImplementedError()
    override suspend fun insert(account: Account): Long = throw NotImplementedError()
    override suspend fun update(account: Account) = throw NotImplementedError()
    override suspend fun delete(account: Account) = throw NotImplementedError()
    override suspend fun reopen(accountId: Long) = throw NotImplementedError()
}
