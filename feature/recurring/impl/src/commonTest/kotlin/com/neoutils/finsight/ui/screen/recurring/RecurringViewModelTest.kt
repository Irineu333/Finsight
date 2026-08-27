@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.recurring

import app.cash.turbine.test
import com.neoutils.finsight.FakeAccountRepository
import com.neoutils.finsight.FakeRecurringOccurrenceRepository
import com.neoutils.finsight.FakeRecurringRepository
import com.neoutils.finsight.consolidationChanges
import com.neoutils.finsight.consolidator
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.RecurringSettledMoney
import com.neoutils.finsight.domain.usecase.GetRecurringMonthOverviewUseCase
import com.neoutils.finsight.domain.usecase.GetUnhandledRecurringUseCase
import com.neoutils.finsight.recurring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class RecurringViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val today = LocalDate(2026, 8, 10)
    private val thisMonth = YearMonth(2026, 8)
    private val lastMonth = YearMonth(2026, 7)

    private val expense = recurring(id = 1L, createdAt = 1L)
    private val income = recurring(id = 2L, type = TransactionType.INCOME, createdAt = 2L)
    private val archived = recurring(id = 3L, createdAt = 3L, isArchived = true)
    private val archivedIncome =
        recurring(id = 4L, type = TransactionType.INCOME, createdAt = 4L, isArchived = true)

    private class ClockOn(private val today: LocalDate) : Clock {
        override fun now(): Instant = today.atStartOfDayIn(TimeZone.currentSystemDefault())
    }

    private fun viewModel(
        repository: FakeRecurringRepository,
        occurrences: FakeRecurringOccurrenceRepository = FakeRecurringOccurrenceRepository(),
        accounts: FakeAccountRepository = FakeAccountRepository(),
    ) = RecurringViewModel(
        recurringRepository = repository,
        accountRepository = accounts,
        occurrenceRepository = occurrences,
        getRecurringMonthOverview = GetRecurringMonthOverviewUseCase(
            getUnhandledRecurring = GetUnhandledRecurringUseCase(),
            occurrenceRepository = occurrences,
        ),
        consolidateMoney = consolidator(),
        observeConsolidationChanges = consolidationChanges(),
        clock = ClockOn(today),
    )

    private suspend fun idsListedBy(
        filter: RecurringFilter,
        recurrings: List<Recurring>,
    ): List<Long> {
        val repository = FakeRecurringRepository()
        repository.all.value = recurrings
        val vm = viewModel(repository)

        var ids: List<Long> = emptyList()
        vm.uiState.test {
            assertIs<RecurringUiState.Loading>(awaitItem())
            vm.onAction(RecurringAction.SelectFilter(filter))
            var state = awaitItem()
            while (state !is RecurringUiState.Content || state.filter != filter) {
                state = awaitItem()
            }
            ids = state.filteredRecurring.map { it.recurring.id }
            cancelAndIgnoreRemainingEvents()
        }
        return ids
    }

    @Test
    fun `active does not include archived`() = runTest(dispatcher) {
        assertEquals(
            listOf(expense.id, income.id),
            idsListedBy(RecurringFilter.ACTIVE, listOf(expense, income, archived)),
        )
    }

    @Test
    fun `archived lists only archived - of both types`() = runTest(dispatcher) {
        assertEquals(
            listOf(archived.id, archivedIncome.id),
            idsListedBy(RecurringFilter.ARCHIVED, listOf(expense, archived, archivedIncome)),
        )
    }

    @Test
    fun `a typed filter excludes the archived of that type`() = runTest(dispatcher) {
        assertEquals(
            listOf(expense.id),
            idsListedBy(RecurringFilter.EXPENSE, listOf(expense, income, archived)),
        )
    }

    @Test
    fun `empty means no recurring at all - not an empty filter`() = runTest(dispatcher) {
        val repository = FakeRecurringRepository()
        repository.all.value = listOf(archived)
        val vm = viewModel(repository)

        vm.uiState.test {
            assertIs<RecurringUiState.Loading>(awaitItem())
            // Only an archived one exists and the default filter is ACTIVE: the list is
            // empty, but the database is not — so this is Content, not Empty.
            val content = assertIs<RecurringUiState.Content>(awaitItem())
            assertEquals(emptyList(), content.filteredRecurring)

            repository.all.value = emptyList()
            assertIs<RecurringUiState.Empty>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * A database with no recurring at all has no month to summarise: the state that
     * offers the creation of the first one carries no overview, and cannot.
     */
    @Test
    fun `an empty database has no summary to offer`() = runTest(dispatcher) {
        val repository = FakeRecurringRepository()
        val vm = viewModel(repository)

        vm.uiState.test {
            assertIs<RecurringUiState.Loading>(awaitItem())
            assertIs<RecurringUiState.Empty>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * **A card template is denominated by the `LIABILITY` account the card projects
     * onto**, and the list resolves the whole chart in one read to find it.
     *
     * The chart is what has to be read, not the account facade: that one is `ASSET`
     * only, and a card's account is not in it. Resolved against the facade, every card
     * template would silently lose its currency and its row would render the unresolved
     * mark — a failure a single-account user would never see and no other test here
     * would catch, because the rest of them use templates that name an account directly.
     */
    @Test
    fun `a card template is denominated by the account behind the card`() = runTest(dispatcher) {
        val cardAccount = Account(
            id = 90L,
            name = "Card",
            type = AccountType.LIABILITY,
            currency = "USD",
        )
        val repository = FakeRecurringRepository()
        repository.all.value = listOf(
            recurring(id = 1L, createdAt = 1L).copy(
                creditCard = CreditCard(
                    id = 7L,
                    name = "Card",
                    limit = 1_000.0,
                    closingDay = 1,
                    dueDay = 10,
                    accountId = cardAccount.id,
                ),
            ),
        )
        val vm = viewModel(repository, accounts = FakeAccountRepository(listOf(cardAccount)))

        vm.uiState.test {
            assertIs<RecurringUiState.Loading>(awaitItem())
            val row = assertIs<RecurringUiState.Content>(awaitItem()).filteredRecurring.single()
            assertEquals("USD", row.amount?.currency)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** The filter governs the list and nothing else — the card has its own control. */
    @Test
    fun `changing the filter moves no figure and no count`() = runTest(dispatcher) {
        val repository = FakeRecurringRepository()
        repository.all.value = listOf(expense, income, archived)
        val occurrences = FakeRecurringOccurrenceRepository(
            settled = RecurringSettledMoney(
                expense = MoneyByCurrency.of("BRL", 1_240.0),
                income = MoneyByCurrency.of("BRL", 865.0),
            ),
        )
        val vm = viewModel(repository, occurrences)

        vm.uiState.test {
            assertIs<RecurringUiState.Loading>(awaitItem())
            val before = assertIs<RecurringUiState.Content>(awaitItem()).summary

            vm.onAction(RecurringAction.SelectFilter(RecurringFilter.ARCHIVED))

            var state = awaitItem()
            while (state !is RecurringUiState.Content || state.filter != RecurringFilter.ARCHIVED) {
                state = awaitItem()
            }
            assertEquals(listOf(archived.id), state.filteredRecurring.map { it.recurring.id })
            assertEquals(before, state.summary)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** And the month governs the card and nothing else. */
    @Test
    fun `changing the month moves the summary and leaves the list alone`() = runTest(dispatcher) {
        val repository = FakeRecurringRepository()
        repository.all.value = listOf(expense, income)
        val occurrences = FakeRecurringOccurrenceRepository()
        // Handled this month, and nothing last month: the same two templates, two
        // different answers about the month.
        occurrences.all.value = listOf(
            occurrence(recurringId = expense.id, month = thisMonth),
            occurrence(recurringId = income.id, month = thisMonth),
        )
        val vm = viewModel(repository, occurrences)

        vm.uiState.test {
            assertIs<RecurringUiState.Loading>(awaitItem())
            val current = assertIs<RecurringUiState.Content>(awaitItem())
            assertEquals(thisMonth, current.selectedYearMonth)
            assertEquals(2, current.summary.handled)

            vm.onAction(RecurringAction.SelectMonth(lastMonth))

            var state = awaitItem()
            while (state !is RecurringUiState.Content || state.selectedYearMonth != lastMonth) {
                state = awaitItem()
            }
            assertEquals(0, state.summary.handled)
            assertEquals(2, state.summary.total)
            assertEquals(
                current.filteredRecurring.map { it.recurring.id },
                state.filteredRecurring.map { it.recurring.id },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun occurrence(
        recurringId: Long,
        month: YearMonth,
        status: RecurringOccurrence.Status = RecurringOccurrence.Status.CONFIRMED,
    ) = RecurringOccurrence(
        recurringId = recurringId,
        cycleNumber = 1,
        yearMonth = month,
        status = status,
        effectiveDate = LocalDate(month.year, month.month, 5),
        handledAt = 0L,
    )
}
