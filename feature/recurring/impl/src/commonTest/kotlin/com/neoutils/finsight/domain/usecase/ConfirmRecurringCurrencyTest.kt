package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.FakeRecurringRepository
import com.neoutils.finsight.StoppedClock
import com.neoutils.finsight.domain.error.RecurringError
import com.neoutils.finsight.domain.exception.RecurringException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import com.neoutils.finsight.domain.repository.RecurringSettledMoney
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **A recurring is denominated by the account it names, and confirming it elsewhere is
 * refused rather than converted** (design D17).
 *
 * This is the one place a facade value could become silently wrong *outside* the ledger:
 * the confirmation defaults to `recurring.amount`, so pointing a template created on a
 * real account at a dollar account would write the raw number down as dollars. Refusing
 * is the answer, not converting — converting would mean choosing a rate for the user in
 * the middle of a confirmation they did not ask a rate for.
 */
class ConfirmRecurringCurrencyTest {

    private val reais = Account(id = 1, name = "Nubank", type = AccountType.ASSET, currency = "BRL")
    private val dollars = Account(id = 2, name = "Chase", type = AccountType.ASSET, currency = "USD")
    private val dollarCardAccount =
        Account(id = 3, name = "Card", type = AccountType.LIABILITY, currency = "USD")

    private val dollarCard = CreditCard(
        id = 10, name = "Amex", limit = 1000.0, closingDay = 5, dueDay = 15,
        accountId = dollarCardAccount.id,
    )

    private val template = Recurring(
        id = 7, type = TransactionType.EXPENSE, amount = 100.0, title = "Netflix",
        dayOfMonth = 5, category = null, account = reais, creditCard = null, createdAt = 0L,
    )

    private val date = LocalDate(2026, 3, 5)

    private fun useCase(
        occurrences: RecordingOccurrences,
        template: Recurring = this.template,
    ) = ConfirmRecurringUseCaseImpl(
        recurringRepository = FakeRecurringRepository(stored = listOf(template)),
        recurringOccurrenceRepository = occurrences,
        getOrCreateInvoiceForMonthUseCase = object : GetOrCreateInvoiceForMonthUseCase {
            override suspend fun invoke(creditCardId: Long, targetDueMonth: YearMonth) =
                throw NotImplementedError("no test here reaches a card invoice")
        },
        accountRepository = FakeAccountRepository(listOf(reais, dollars, dollarCardAccount)),
        clock = StoppedClock(date.atStartOfDayIn(TimeZone.currentSystemDefault())),
    )

    @Test
    fun `confirming on the template's own account goes through`() = runTest {
        val occurrences = RecordingOccurrences()

        val result = useCase(occurrences)(recurring = template, date = date)

        assertTrue(result.isRight())
        assertEquals(reais.id, occurrences.recorded?.legs?.single()?.accountId)
    }

    @Test
    fun `redirecting to an account of another currency is refused`() = runTest {
        val occurrences = RecordingOccurrences()

        val result = useCase(occurrences)(recurring = template, date = date, account = dollars)

        val error = assertIs<Either.Left<Throwable>>(result).value
        assertIs<RecurringException>(error)
        assertEquals(RecurringError.CURRENCY_MISMATCH, error.error)
        assertNull(occurrences.recorded, "nothing is written when the redirection is refused")
    }

    @Test
    fun `redirecting to a card of another currency is refused too`() = runTest {
        val occurrences = RecordingOccurrences()

        val result = useCase(occurrences)(
            recurring = template,
            date = date,
            target = TransactionTarget.CREDIT_CARD,
            creditCard = dollarCard,
        )

        val error = assertIs<Either.Left<Throwable>>(result).value
        assertIs<RecurringException>(error)
        assertEquals(RecurringError.CURRENCY_MISMATCH, error.error)
        assertNull(occurrences.recorded)
    }

    /**
     * A template whose account is gone denominates nothing, so there is nothing for the
     * redirection to disagree with. That is a different failure with a different message,
     * and this guard stays out of it.
     */
    @Test
    fun `a template that names no account is not refused by this guard`() = runTest {
        val occurrences = RecordingOccurrences()
        val orphan = template.copy(account = null)

        val result = useCase(occurrences, template = orphan)(
            recurring = orphan,
            date = date,
            account = dollars,
        )

        assertTrue(result.isRight())
        assertEquals(dollars.id, occurrences.recorded?.legs?.single()?.accountId)
    }
}

private class RecordingOccurrences : IRecurringOccurrenceRepository {
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

    /** Not exercised here: a fake that starts answering it says so instead of passing. */
    override suspend fun settledIn(month: YearMonth): RecurringSettledMoney =
        throw NotImplementedError()
}

private class FakeAccountRepository(private val accounts: List<Account>) : IAccountRepository {
    override suspend fun hasYieldingAccount(): Boolean = false
    override suspend fun getAccountById(accountId: Long): Account? =
        accounts.firstOrNull { it.id == accountId }

    override fun observeAllAccounts(): Flow<List<Account>> = flowOf(accounts)
    override suspend fun getAllAccounts(): List<Account> = accounts
    override suspend fun getAllAccountsIncludingClosed(): List<Account> = accounts
    override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = flowOf(accounts)
    override suspend fun getAllLedgerAccounts(): List<Account> = accounts
    override fun observeAllLedgerAccounts(): Flow<List<Account>> = flowOf(accounts)
    override fun observeAccountById(accountId: Long): Flow<Account?> = flowOf(getById(accountId))
    override suspend fun getDefaultAccount(): Account? = accounts.firstOrNull { it.isDefault }
    override fun observeDefaultAccount(): Flow<Account?> = flowOf(accounts.firstOrNull { it.isDefault })
    override suspend fun getAccountCount(): Int = accounts.size
    override suspend fun insert(account: Account): Long = throw NotImplementedError()
    override suspend fun update(account: Account) = throw NotImplementedError()
    override suspend fun delete(account: Account) = throw NotImplementedError()
    override suspend fun reopen(accountId: Long) = throw NotImplementedError()

    private fun getById(accountId: Long) = accounts.firstOrNull { it.id == accountId }
}
