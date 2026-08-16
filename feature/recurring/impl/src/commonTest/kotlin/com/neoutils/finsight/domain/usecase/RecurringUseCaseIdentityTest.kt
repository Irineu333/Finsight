package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.FakeBudgetRepository
import com.neoutils.finsight.FakeRecurringRepository
import com.neoutils.finsight.StoppedClock
import com.neoutils.finsight.domain.error.RecurringError
import com.neoutils.finsight.domain.exception.RecurringException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.model.RecurringRetirability
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The recurring use cases are identified by **id**, and that form is the one that
 * carries the rule.
 *
 * Two properties are asserted of each, and they are the pair that makes the second form
 * safe to offer: an identity matching nothing is refused with `NOT_FOUND` before anything
 * is written, and the two forms of one use case produce the same result for the same
 * identity — because the one taking the recurring only extracts its id.
 *
 * Resolving at execution rather than trusting what the caller holds is what the last two
 * tests are about: a template a sheet loaded is a reading that can already be out of date,
 * and the operation has to act on the recurring as it is when it runs.
 */
class RecurringUseCaseIdentityTest {

    private val account = Account(id = 1, name = "Nubank", type = AccountType.ASSET, currency = "BRL")

    private val template = Recurring(
        id = 7,
        type = TransactionType.EXPENSE,
        amount = 100.0,
        title = "Netflix",
        dayOfMonth = 5,
        category = null,
        account = account,
        creditCard = null,
        createdAt = 0L,
    )

    private val absent = template.copy(id = 404)
    private val date = LocalDate(2026, 3, 5)

    private fun repository(vararg stored: Recurring) =
        FakeRecurringRepository(stored = stored.toList())

    private fun confirm(
        repository: FakeRecurringRepository,
        occurrences: RecordingConfirmations,
    ) = ConfirmRecurringUseCaseImpl(
        recurringRepository = repository,
        recurringOccurrenceRepository = occurrences,
        getOrCreateInvoiceForMonthUseCase = object : GetOrCreateInvoiceForMonthUseCase {
            override suspend fun invoke(creditCardId: Long, targetDueMonth: YearMonth) =
                throw NotImplementedError("no test here reaches a card invoice")
        },
        accountRepository = KnownAccounts(listOf(account)),
        clock = StoppedClock(),
    )

    // region — an identity that matches nothing

    @Test
    fun `archiving a recurring that does not exist is refused and nothing is written`() = runTest {
        val repository = repository(template)

        val error = assertIs<RecurringException>(
            ArchiveRecurringUseCaseImpl(repository)(absent.id).leftOrNull()
        )

        assertEquals(RecurringError.NOT_FOUND, error.error)
        assertTrue(repository.updated.isEmpty(), "nothing may be written")
    }

    @Test
    fun `unarchiving a recurring that does not exist is refused and nothing is written`() = runTest {
        val repository = repository(template.copy(isArchived = true))

        val error = assertIs<RecurringException>(
            UnarchiveRecurringUseCaseImpl(repository)(absent.id).leftOrNull()
        )

        assertEquals(RecurringError.NOT_FOUND, error.error)
        assertTrue(repository.updated.isEmpty(), "nothing may be written")
    }

    @Test
    fun `deleting a recurring that does not exist is refused and nothing is removed`() = runTest {
        val repository = repository(template)
        val useCase = DeleteRecurringUseCaseImpl(
            repository = repository,
            resolveRetirability = ResolveRecurringRetirabilityUseCaseImpl(
                recurringRepository = repository,
                budgetRepository = FakeBudgetRepository(),
            ),
        )

        val error = assertIs<RecurringException>(useCase(absent.id).leftOrNull())

        assertEquals(RecurringError.NOT_FOUND, error.error)
        assertTrue(repository.deleted.isEmpty(), "nothing may be removed")
    }

    @Test
    fun `skipping a cycle of a recurring that does not exist is refused`() = runTest {
        val occurrences = RecordingSkips()
        val useCase = SkipRecurringUseCaseImpl(
            recurringRepository = repository(template),
            recurringOccurrenceRepository = occurrences,
            clock = StoppedClock(),
        )

        val error = assertIs<RecurringException>(useCase(absent.id, date).leftOrNull())

        assertEquals(RecurringError.NOT_FOUND, error.error)
        assertTrue(occurrences.saved.isEmpty(), "no occurrence may be written")
    }

    @Test
    fun `confirming a cycle of a recurring that does not exist is refused`() = runTest {
        val occurrences = RecordingConfirmations()

        val error = assertIs<RecurringException>(
            confirm(repository(template), occurrences)(recurringId = absent.id, date = date)
                .leftOrNull()
        )

        assertEquals(RecurringError.NOT_FOUND, error.error)
        assertNull(occurrences.recorded, "nothing may be written")
    }

    @Test
    fun `editing a recurring that does not exist is refused and nothing is written`() = runTest {
        val repository = repository(template)

        val error = assertIs<RecurringException>(
            SaveRecurringUseCaseImpl(repository)(
                id = absent.id,
                type = TransactionType.EXPENSE,
                amount = "150,00",
                title = "Rent",
                dayOfMonth = "5",
                category = null,
                account = account,
                creditCard = null,
            ).leftOrNull()
        )

        assertEquals(RecurringError.NOT_FOUND, error.error)
        assertTrue(repository.updated.isEmpty(), "nothing may be written")
    }

    // endregion

    // region — the two forms are one operation

    @Test
    fun `archiving by id and by recurring are the same operation`() = runTest {
        val byId = repository(template)
        val byRecurring = repository(template)

        val fromId = ArchiveRecurringUseCaseImpl(byId)(template.id)
        val fromRecurring = ArchiveRecurringUseCaseImpl(byRecurring)(template)

        assertEquals(fromId.isRight(), fromRecurring.isRight())
        assertEquals(byId.updated, byRecurring.updated)
    }

    @Test
    fun `unarchiving by id and by recurring are the same operation`() = runTest {
        val archived = template.copy(isArchived = true)
        val byId = repository(archived)
        val byRecurring = repository(archived)

        val fromId = UnarchiveRecurringUseCaseImpl(byId)(archived.id)
        val fromRecurring = UnarchiveRecurringUseCaseImpl(byRecurring)(archived)

        assertEquals(fromId.isRight(), fromRecurring.isRight())
        assertEquals(byId.updated, byRecurring.updated)
    }

    @Test
    fun `deleting by id and by recurring are the same operation`() = runTest {
        val byId = repository(template)
        val byRecurring = repository(template)

        val fromId = DeleteRecurringUseCaseImpl(
            repository = byId,
            resolveRetirability = ResolveRecurringRetirabilityUseCaseImpl(byId, FakeBudgetRepository()),
        )(template.id)

        val fromRecurring = DeleteRecurringUseCaseImpl(
            repository = byRecurring,
            resolveRetirability = ResolveRecurringRetirabilityUseCaseImpl(
                byRecurring,
                FakeBudgetRepository(),
            ),
        )(template)

        assertEquals(fromId.isRight(), fromRecurring.isRight())
        assertEquals(byId.deleted, byRecurring.deleted)
    }

    @Test
    fun `resolving retirability by id and by recurring are the same read`() = runTest {
        val useCase = ResolveRecurringRetirabilityUseCaseImpl(
            recurringRepository = FakeRecurringRepository(
                stored = listOf(template),
                hasTransaction = true,
            ),
            budgetRepository = FakeBudgetRepository(),
        )

        assertEquals(useCase(template.id), useCase(template))
        assertIs<RecurringRetirability.MustArchive>(useCase(template.id))
    }

    @Test
    fun `skipping by id and by recurring are the same operation`() = runTest {
        val byId = RecordingSkips()
        val byRecurring = RecordingSkips()

        val fromId = SkipRecurringUseCaseImpl(repository(template), byId, StoppedClock())(template.id, date)
        val fromRecurring = SkipRecurringUseCaseImpl(repository(template), byRecurring, StoppedClock())(template, date)

        assertEquals(fromId.isRight(), fromRecurring.isRight())
        assertEquals(byId.saved, byRecurring.saved)
    }

    @Test
    fun `confirming by id and by recurring are the same operation`() = runTest {
        val byId = RecordingConfirmations()
        val byRecurring = RecordingConfirmations()

        val fromId = confirm(repository(template), byId)(recurringId = template.id, date = date)
        val fromRecurring = confirm(repository(template), byRecurring)(recurring = template, date = date)

        assertEquals(fromId.isRight(), fromRecurring.isRight())
        assertEquals(byId.recorded, byRecurring.recorded)
    }

    // endregion

    // region — resolved when the operation runs

    @Test
    fun `the recurring is read as it is when the action runs, not as the caller holds it`() = runTest {
        // The caller's copy still says 100; the stored one has since been edited to 250.
        // Resolving at execution is what makes the confirmation post the second.
        val occurrences = RecordingConfirmations()
        val stored = template.copy(amount = 250.0)

        val result = confirm(repository(stored), occurrences)(recurring = template, date = date)

        assertTrue(result.isRight())
        assertEquals(250.0, occurrences.recorded?.legs?.single()?.amount)
    }

    /**
     * The absence of a title means what the confirmation sheet has always meant by it —
     * a cycle with no name of its own, displayed by its category — and it now means that
     * because the use case says so, not because one caller remembered to normalise.
     * Blank, whitespace and omission all arrive at the same place, for every caller.
     */
    @Test
    fun `a blank title is an absence, and never the template's own`() = runTest {
        val blank = RecordingConfirmations()
        val omitted = RecordingConfirmations()

        confirm(repository(template), blank)(recurring = template, date = date, title = "   ")
        confirm(repository(template), omitted)(recurring = template, date = date)

        assertNull(blank.recorded?.title, "the template's title is never substituted back in")
        assertEquals(omitted.recorded?.title, blank.recorded?.title)
        assertEquals("Netflix", template.title, "the recurring is read, never written")
    }

    @Test
    fun `a title with surrounding space is trimmed by the use case, not by its caller`() = runTest {
        val occurrences = RecordingConfirmations()

        confirm(repository(template), occurrences)(
            recurring = template,
            date = date,
            title = "  Consulta  ",
        )

        assertEquals("Consulta", occurrences.recorded?.title)
    }

    // endregion
}

private class RecordingSkips : IRecurringOccurrenceRepository {
    val saved = mutableListOf<RecurringOccurrence>()

    override suspend fun save(occurrence: RecurringOccurrence): Long {
        saved += occurrence
        return occurrence.recurringId
    }

    override suspend fun confirmCycle(
        intent: TransactionIntent,
        occurrence: RecurringOccurrence,
    ): Transaction = throw NotImplementedError()

    override fun observeAllOccurrences(): Flow<List<RecurringOccurrence>> = flowOf(emptyList())
    override suspend fun getAllOccurrences(): List<RecurringOccurrence> = emptyList()
    override suspend fun getOccurrenceBy(recurringId: Long, yearMonth: YearMonth): RecurringOccurrence? = null
    override suspend fun getOccurrenceBy(recurringId: Long, cycleNumber: Int): RecurringOccurrence? = null
}

private class RecordingConfirmations : IRecurringOccurrenceRepository {
    var recorded: TransactionIntent? = null

    override suspend fun confirmCycle(
        intent: TransactionIntent,
        occurrence: RecurringOccurrence,
    ): Transaction {
        recorded = intent
        return Transaction(id = 1, title = intent.title, date = intent.date, entries = emptyList())
    }

    override fun observeAllOccurrences(): Flow<List<RecurringOccurrence>> = flowOf(emptyList())
    override suspend fun getAllOccurrences(): List<RecurringOccurrence> = emptyList()
    override suspend fun getOccurrenceBy(recurringId: Long, yearMonth: YearMonth): RecurringOccurrence? = null
    override suspend fun getOccurrenceBy(recurringId: Long, cycleNumber: Int): RecurringOccurrence? = null
    override suspend fun save(occurrence: RecurringOccurrence): Long = throw NotImplementedError()
}

private class KnownAccounts(private val accounts: List<Account>) : IAccountRepository {
    override suspend fun hasYieldingAccount(): Boolean = false
    override suspend fun getAccountById(accountId: Long): Account? =
        accounts.firstOrNull { it.id == accountId }

    override fun observeAllAccounts(): Flow<List<Account>> = flowOf(accounts)
    override suspend fun getAllAccounts(): List<Account> = accounts
    override suspend fun getAllAccountsIncludingClosed(): List<Account> = accounts
    override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = flowOf(accounts)
    override suspend fun getAllLedgerAccounts(): List<Account> = accounts
    override fun observeAllLedgerAccounts(): Flow<List<Account>> = flowOf(accounts)
    override fun observeAccountById(accountId: Long): Flow<Account?> =
        flowOf(accounts.firstOrNull { it.id == accountId })

    override suspend fun getDefaultAccount(): Account? = accounts.firstOrNull { it.isDefault }
    override fun observeDefaultAccount(): Flow<Account?> = flowOf(accounts.firstOrNull { it.isDefault })
    override suspend fun getAccountCount(): Int = accounts.size
    override suspend fun insert(account: Account): Long = throw NotImplementedError()
    override suspend fun update(account: Account) = throw NotImplementedError()
    override suspend fun delete(account: Account) = throw NotImplementedError()
    override suspend fun reopen(accountId: Long) = throw NotImplementedError()
}
