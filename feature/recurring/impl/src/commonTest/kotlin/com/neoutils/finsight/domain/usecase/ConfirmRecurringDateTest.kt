@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.FakeAccountRepository
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
import kotlin.time.ExperimentalTime

/**
 * **A cycle is confirmed on a day that has already come, and this is the only write that
 * has nowhere else to say so.**
 *
 * Every other path to the ledger goes through a form that owns the rule: a transaction is
 * refused with `BuildTransactionError.DateFuture` and a transfer with
 * `TransferError.FutureDate`. A confirmation builds no form — the `date` reaches the
 * posting and the occurrence directly — so the same posting was inadmissible through one
 * door and admissible through this one, and the rest of the app reads dated rows assuming
 * none of them is ahead of today.
 *
 * The date picker of the confirmation offers nothing after today (`confirmableDates`), so
 * this is unreachable by the designed path. It is the net behind it, in the same shape as
 * the amount and the currency refusals beside it — and every caller that does not go
 * through the picker (a tool, a test, a use case written tomorrow) reaches the domain
 * directly.
 */
class ConfirmRecurringDateTest {

    private val account = Account(id = 1, name = "Nubank", type = AccountType.ASSET, currency = "BRL")
    private val cardAccount =
        Account(id = 2, name = "Card", type = AccountType.LIABILITY, currency = "BRL")

    private val card = CreditCard(
        id = 10, name = "Nubank", limit = 1000.0, closingDay = 5, dueDay = 15,
        accountId = cardAccount.id,
    )

    private val template = Recurring(
        id = 7, type = TransactionType.EXPENSE, amount = 100.0, title = "Netflix",
        dayOfMonth = 5, category = null, account = account, creditCard = null, createdAt = 0L,
    )

    private val today = LocalDate(2026, 3, 5)
    private val tomorrow = LocalDate(2026, 3, 6)

    private fun useCase(
        occurrences: RecordingDatedCycles,
        invoices: UnreachedInvoices = UnreachedInvoices(),
    ) = ConfirmRecurringUseCaseImpl(
        recurringRepository = FakeRecurringRepository(stored = listOf(template)),
        recurringOccurrenceRepository = occurrences,
        getOrCreateInvoiceForMonthUseCase = invoices,
        accountRepository = FakeAccountRepository(listOf(account, cardAccount)),
        // Today is stated, never read off the system: the day the confirmation is
        // compared against is the same decision the rest of the app takes once.
        clock = StoppedClock(today.atStartOfDayIn(TimeZone.currentSystemDefault())),
    )

    @Test
    fun `a cycle dated after today is refused and nothing is written`() = runTest {
        val occurrences = RecordingDatedCycles()

        val result = useCase(occurrences)(recurring = template, date = tomorrow)

        val error = assertIs<Either.Left<Throwable>>(result).value
        assertIs<RecurringException>(error)
        assertEquals(RecurringError.DATE_IN_FUTURE, error.error)
        assertNull(occurrences.recorded, "a cycle dated ahead of today reached the ledger")
    }

    /**
     * The refusal sits where the amount rule sits, and for the same reason: an invoice
     * opened for a cycle that is then refused outlives the refusal (design D7).
     */
    @Test
    fun `a cycle aimed at a card is refused before an invoice is opened for it`() = runTest {
        val occurrences = RecordingDatedCycles()
        val invoices = UnreachedInvoices()

        val result = useCase(occurrences, invoices)(
            recurring = template,
            date = tomorrow,
            target = TransactionTarget.CREDIT_CARD,
            creditCard = card,
        )

        assertTrue(result.isLeft())
        assertEquals(0, invoices.calls, "an invoice opened here would outlive the refusal")
        assertNull(occurrences.recorded)
    }

    /** Today itself is not the future, and the day the picker lands on still posts. */
    @Test
    fun `a cycle dated today still goes through`() = runTest {
        val occurrences = RecordingDatedCycles()

        val result = useCase(occurrences)(recurring = template, date = today)

        assertTrue(result.isRight(), "the day the confirmation is offered was refused")
        assertEquals(today, occurrences.recorded?.date)
    }
}

private class RecordingDatedCycles : IRecurringOccurrenceRepository {
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
    override suspend fun settledIn(month: YearMonth): RecurringSettledMoney = RecurringSettledMoney.none
    override suspend fun save(occurrence: RecurringOccurrence): Long = throw NotImplementedError()
}

/** Counts what the refusal is supposed to have prevented: opening an invoice for the cycle. */
private class UnreachedInvoices : GetOrCreateInvoiceForMonthUseCase {
    var calls = 0

    override suspend fun invoke(
        creditCardId: Long,
        targetDueMonth: YearMonth,
    ): Either<Throwable, Invoice> {
        calls++
        throw NotImplementedError("no test here expects to reach an invoice")
    }
}
