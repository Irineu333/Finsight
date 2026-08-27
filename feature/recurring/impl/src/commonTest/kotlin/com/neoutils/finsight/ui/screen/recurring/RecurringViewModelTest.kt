@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.recurring

import app.cash.turbine.test
import com.neoutils.finsight.FakeAccountRepository
import com.neoutils.finsight.FakeCategoryRepository
import com.neoutils.finsight.FakeRecurringOccurrenceRepository
import com.neoutils.finsight.FakeRecurringRepository
import com.neoutils.finsight.FakeTransactionsByIds
import com.neoutils.finsight.consolidationChanges
import com.neoutils.finsight.consolidator
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringCycleStatus
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.RecurringSettledMoney
import com.neoutils.finsight.domain.usecase.GetRecurringCyclesUseCase
import com.neoutils.finsight.domain.usecase.GetRecurringMonthOverviewUseCase
import com.neoutils.finsight.domain.usecase.GetUnhandledRecurringUseCase
import com.neoutils.finsight.recurring
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * **What the screen lists is the month's cycles, in four groups.**
 *
 * The month governs both halves of the screen, the cut by nature governs only the list,
 * and the group a row lands in decides which of two things the row is read from — the
 * template that projects the cycle, or the ledger that recorded it.
 */
class RecurringViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val today = LocalDate(2026, 8, 10)
    private val thisMonth = YearMonth(2026, 8)
    private val lastMonth = YearMonth(2026, 7)

    private val wallet = Account(id = 1L, name = "Wallet", type = AccountType.ASSET, currency = "BRL")
    private val expenseAccount =
        Account(id = 4L, name = "expense", type = AccountType.EXPENSE, currency = "BRL")

    private val groceries = Category(
        id = 7L,
        name = "Mercado",
        icon = CategoryLazyIcon("food"),
        type = Category.Type.EXPENSE,
        createdAt = 0L,
        dimensionId = 70L,
    )

    // Due on the 5th, in a month whose 10th it is: past due unless something handled it.
    private val expense = recurring(id = 1L, createdAt = 1L).copy(account = wallet)
    private val income = recurring(id = 2L, type = TransactionType.INCOME, createdAt = 2L)
        .copy(account = wallet)
    private val archived = recurring(id = 3L, createdAt = 3L, isArchived = true).copy(account = wallet)

    private class ClockOn(private val today: LocalDate) : Clock {
        override fun now(): Instant = today.atStartOfDayIn(TimeZone.currentSystemDefault())
    }

    private fun viewModel(
        repository: FakeRecurringRepository,
        occurrences: FakeRecurringOccurrenceRepository = FakeRecurringOccurrenceRepository(),
        accounts: FakeAccountRepository = FakeAccountRepository(listOf(wallet, expenseAccount)),
        transactions: FakeTransactionsByIds = FakeTransactionsByIds(),
        categories: FakeCategoryRepository = FakeCategoryRepository(listOf(groceries)),
    ) = RecurringViewModel(
        recurringRepository = repository,
        accountRepository = accounts,
        categoryRepository = categories,
        transactionRepository = transactions,
        occurrenceRepository = occurrences,
        getRecurringCycles = GetRecurringCyclesUseCase(GetUnhandledRecurringUseCase()),
        getRecurringMonthOverview = GetRecurringMonthOverviewUseCase(occurrenceRepository = occurrences),
        consolidateMoney = consolidator(),
        observeConsolidationChanges = consolidationChanges(),
        clock = ClockOn(today),
    )

    private fun occurrence(
        recurringId: Long,
        month: YearMonth = thisMonth,
        status: RecurringOccurrence.Status = RecurringOccurrence.Status.CONFIRMED,
        transactionId: Long? = null,
        day: Int = 5,
    ) = RecurringOccurrence(
        recurringId = recurringId,
        cycleNumber = 1,
        yearMonth = month,
        status = status,
        transactionId = transactionId,
        effectiveDate = LocalDate(month.year, month.month, day),
        handledAt = 0L,
    )

    private fun transaction(
        id: Long,
        title: String?,
        cents: Long,
        date: LocalDate = LocalDate(2026, 8, 5),
    ) = Transaction(
        id = id,
        title = title,
        date = date,
        recurringId = null,
        entries = listOf(
            Entry(account = wallet, amount = -cents),
            Entry(account = expenseAccount, amount = cents, dimensionId = groceries.dimensionId),
        ),
    )

    /** Every row of every section, in the order the screen renders them. */
    private val RecurringUiState.Content.listedIds: List<Long>
        get() = sections.flatMap { section -> section.cycles.map { it.recurring.id } }

    private suspend fun contentOf(
        recurrings: List<Recurring>,
        occurrences: FakeRecurringOccurrenceRepository = FakeRecurringOccurrenceRepository(),
        transactions: FakeTransactionsByIds = FakeTransactionsByIds(),
        filter: RecurringFilter? = null,
        month: YearMonth? = null,
    ): RecurringUiState.Content {
        val repository = FakeRecurringRepository()
        repository.all.value = recurrings
        val vm = viewModel(repository, occurrences, transactions = transactions)

        var content: RecurringUiState.Content? = null
        vm.uiState.test {
            assertIs<RecurringUiState.Loading>(awaitItem())
            filter?.let { vm.onAction(RecurringAction.SelectFilter(it)) }
            month?.let { vm.onAction(RecurringAction.SelectMonth(it)) }

            var state = awaitItem()
            while (
                state !is RecurringUiState.Content ||
                (filter != null && state.filter != filter) ||
                (month != null && state.selectedYearMonth != month)
            ) {
                state = awaitItem()
            }
            content = state
            cancelAndIgnoreRemainingEvents()
        }
        return content!!
    }

    // --- The list is of cycles, and archived templates have none ---------------------

    @Test
    fun `an archived template is in no section, in any month`() = runTest(dispatcher) {
        assertEquals(
            listOf(expense.id, income.id),
            contentOf(listOf(expense, income, archived)).listedIds,
        )

        assertEquals(
            listOf(expense.id, income.id),
            contentOf(listOf(expense, income, archived), month = lastMonth).listedIds,
        )
    }

    @Test
    fun `the cut by nature narrows every section and reorders none`() = runTest(dispatcher) {
        val content = contentOf(listOf(expense, income), filter = RecurringFilter.EXPENSE)

        assertEquals(listOf(expense.id), content.listedIds)
        assertEquals(listOf(RecurringCycleStatus.PENDING), content.sections.map { it.status })
    }

    @Test
    fun `empty means no recurring at all - not a month with no cycle`() = runTest(dispatcher) {
        val repository = FakeRecurringRepository()
        repository.all.value = listOf(archived)
        val vm = viewModel(repository)

        vm.uiState.test {
            assertIs<RecurringUiState.Loading>(awaitItem())
            // Only an archived one exists: the list has no section, but the database is
            // not empty — so this is Content, not Empty.
            val content = assertIs<RecurringUiState.Content>(awaitItem())
            assertEquals(emptyList(), content.sections)

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

    // --- The sections themselves -----------------------------------------------------

    /**
     * The order is by how much each group asks of the user, and a group with nothing in
     * it is not rendered at all: an empty section would be the summary card asserting an
     * absence it already asserts.
     */
    @Test
    fun `the sections come in order, and an empty one is absent`() = runTest(dispatcher) {
        val ahead = recurring(id = 4L, createdAt = 4L).copy(dayOfMonth = 28, account = wallet)
        val skipped = recurring(id = 5L, createdAt = 5L).copy(account = wallet)
        val occurrences = FakeRecurringOccurrenceRepository()
        occurrences.all.value = listOf(
            occurrence(skipped.id, status = RecurringOccurrence.Status.SKIPPED),
        )

        val content = contentOf(listOf(expense, ahead, skipped), occurrences)

        assertEquals(
            listOf(
                RecurringCycleStatus.PENDING,
                RecurringCycleStatus.UPCOMING,
                RecurringCycleStatus.SKIPPED,
            ),
            content.sections.map { it.status },
        )
    }

    /** Under a heading, the order is the cycle's date — never the template's creation. */
    @Test
    fun `a section is ordered by the cycle date`() = runTest(dispatcher) {
        // Both fall behind today's 10th, so they share one section and nothing but the
        // ordering inside it can produce this answer — created in the reverse order of
        // their days precisely so the two rules disagree.
        val eighth = recurring(id = 1L, createdAt = 1L).copy(dayOfMonth = 8, account = wallet)
        val fifth = recurring(id = 2L, createdAt = 2L).copy(dayOfMonth = 5, account = wallet)

        val content = contentOf(listOf(eighth, fifth))

        assertEquals(listOf(RecurringCycleStatus.PENDING), content.sections.map { it.status })
        assertEquals(listOf(fifth.id, eighth.id), content.listedIds)
    }

    // --- The posted section reads the ledger ------------------------------------------

    /**
     * **The whole point of the posted section.** The template asks for 940 and is called
     * "Aluguel"; the cycle was confirmed for 865 under another name. What the month did is
     * the transaction, and the row says so.
     */
    @Test
    fun `a posted cycle reads its value and its title from the ledger`() = runTest(dispatcher) {
        val template = recurring(id = 1L, amount = 940.0, createdAt = 1L).copy(account = wallet)
        val occurrences = FakeRecurringOccurrenceRepository()
        occurrences.all.value = listOf(occurrence(template.id, transactionId = 100L))

        val content = contentOf(
            recurrings = listOf(template),
            occurrences = occurrences,
            transactions = FakeTransactionsByIds(
                listOf(transaction(id = 100L, title = "Aluguel + condomínio", cents = 86_500)),
            ),
        )

        val row = assertIs<RecurringCycleUi.Posted>(content.sections.single().cycles.single())
        assertEquals(RecurringCycleStatus.POSTED, content.sections.single().status)
        assertEquals(865.0, row.transaction.amount.value)
        assertEquals("Aluguel + condomínio", row.transaction.title)
        assertEquals("BRL", row.transaction.amount.currency)
    }

    /** Without a title of its own, the ledger row is named by the category of the entry. */
    @Test
    fun `a posted cycle with no title is named by its category`() = runTest(dispatcher) {
        val template = recurring(id = 1L, createdAt = 1L).copy(account = wallet)
        val occurrences = FakeRecurringOccurrenceRepository()
        occurrences.all.value = listOf(occurrence(template.id, transactionId = 100L))

        val content = contentOf(
            recurrings = listOf(template),
            occurrences = occurrences,
            transactions = FakeTransactionsByIds(
                listOf(transaction(id = 100L, title = null, cents = 12_000)),
            ),
        )

        val row = assertIs<RecurringCycleUi.Posted>(content.sections.single().cycles.single())
        assertEquals(groceries.name, row.transaction.title)
    }

    /**
     * The money left, it is in the ledger, and it has a currency: a template that lost the
     * account it named has nothing to say about that, and the row must not fall back to
     * the unresolved mark it would show while the cycle was still owed.
     */
    @Test
    fun `a posted cycle of a template that lost its account still has a figure`() = runTest(dispatcher) {
        val orphan = recurring(id = 1L, amount = 940.0, createdAt = 1L)
        val occurrences = FakeRecurringOccurrenceRepository()
        occurrences.all.value = listOf(occurrence(orphan.id, transactionId = 100L))

        val content = contentOf(
            recurrings = listOf(orphan),
            occurrences = occurrences,
            transactions = FakeTransactionsByIds(
                listOf(transaction(id = 100L, title = "Aluguel", cents = 86_500)),
            ),
        )

        val row = assertIs<RecurringCycleUi.Posted>(content.sections.single().cycles.single())
        assertEquals(865.0, row.transaction.amount.value)
        assertEquals("BRL", row.transaction.amount.currency)
    }

    /** ...while a cycle that has not been posted keeps the unresolved figure. */
    @Test
    fun `a pending cycle of a template that lost its account has no figure to show`() = runTest(dispatcher) {
        val orphan = recurring(id = 1L, amount = 940.0, createdAt = 1L)

        val content = contentOf(listOf(orphan))

        val row = assertIs<RecurringCycleUi.Template>(content.sections.single().cycles.single())
        assertNull(row.amount)
    }

    /** One read for the whole section, never one per row. */
    @Test
    fun `the posted section costs one ledger query`() = runTest(dispatcher) {
        val first = recurring(id = 1L, createdAt = 1L).copy(account = wallet)
        val second = recurring(id = 2L, createdAt = 2L).copy(account = wallet)
        val occurrences = FakeRecurringOccurrenceRepository()
        occurrences.all.value = listOf(
            occurrence(first.id, transactionId = 100L),
            occurrence(second.id, transactionId = 101L),
        )
        val transactions = FakeTransactionsByIds(
            listOf(
                transaction(id = 100L, title = "A", cents = 1_000),
                transaction(id = 101L, title = "B", cents = 2_000),
            ),
        )

        contentOf(listOf(first, second), occurrences, transactions)

        assertTrue(transactions.asked.isNotEmpty())
        assertTrue(transactions.asked.all { it.toSet() == setOf(100L, 101L) })
    }

    // --- The two controls -------------------------------------------------------------

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
            val content = assertIs<RecurringUiState.Content>(awaitItem())
            val row = assertIs<RecurringCycleUi.Template>(content.sections.single().cycles.single())
            assertEquals("USD", row.amount?.currency)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** The cut by nature governs the list and nothing else — the card has its own control. */
    @Test
    fun `changing the filter moves no figure`() = runTest(dispatcher) {
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

            vm.onAction(RecurringAction.SelectFilter(RecurringFilter.INCOME))

            var state = awaitItem()
            while (state !is RecurringUiState.Content || state.filter != RecurringFilter.INCOME) {
                state = awaitItem()
            }
            assertEquals(listOf(income.id), state.listedIds)
            assertEquals(before, state.summary)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** And the month governs **both**: the two halves answer for the same month. */
    @Test
    fun `changing the month moves the summary and the list together`() = runTest(dispatcher) {
        val repository = FakeRecurringRepository()
        repository.all.value = listOf(expense, income)
        val occurrences = FakeRecurringOccurrenceRepository()
        // Both handled this month, and nothing last month: the same two templates, two
        // different answers about the month.
        occurrences.all.value = listOf(
            occurrence(expense.id, month = thisMonth, status = RecurringOccurrence.Status.SKIPPED),
            occurrence(income.id, month = thisMonth, status = RecurringOccurrence.Status.SKIPPED),
        )
        val vm = viewModel(repository, occurrences)

        vm.uiState.test {
            assertIs<RecurringUiState.Loading>(awaitItem())
            val current = assertIs<RecurringUiState.Content>(awaitItem())
            assertEquals(thisMonth, current.selectedYearMonth)
            assertEquals(
                listOf(RecurringCycleStatus.SKIPPED),
                current.sections.map { it.status },
            )

            vm.onAction(RecurringAction.SelectMonth(lastMonth))

            var state = awaitItem()
            while (state !is RecurringUiState.Content || state.selectedYearMonth != lastMonth) {
                state = awaitItem()
            }
            // Last month has no occurrence at all, and its dates are behind today.
            assertEquals(
                listOf(RecurringCycleStatus.PENDING),
                state.sections.map { it.status },
            )
            assertEquals(listOf(expense.id, income.id), state.listedIds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * A month the series had not begun in has no cycle, and the screen does not become an
     * empty state over it: the summary is precisely what still has something to say.
     */
    @Test
    fun `a month with no cycle keeps the summary and shows no section`() = runTest(dispatcher) {
        val bornInAugust = recurring(id = 1L).copy(
            account = wallet,
            createdAt = LocalDate(2026, 8, 1)
                .atStartOfDayIn(TimeZone.currentSystemDefault())
                .toEpochMilliseconds(),
        )

        val content = contentOf(listOf(bornInAugust), month = YearMonth(2026, 3))

        assertEquals(emptyList(), content.sections)
        assertEquals(YearMonth(2026, 3), content.selectedYearMonth)
    }
}
