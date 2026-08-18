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
 * **A cycle is worth more than nothing, and this is the only write that has nowhere else
 * to say so.**
 *
 * Every other path to the ledger goes through a form that owns the rule. A confirmation
 * does not: the optional `amount` says what this cycle was actually worth, and it reaches
 * the posting leg directly. `type` is what says the direction, so a negative amount is the
 * cycle posted on the other side — an expense of minus forty raises the balance.
 *
 * The refusal comes **before the invoice is resolved**, which is the part that could not
 * simply be moved: resolving one creates it as a deliberate side effect outside the unit
 * of work (design D7), so a later refusal would leave an invoice behind for a cycle that
 * never posted.
 */
class ConfirmRecurringAmountTest {

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

    private val date = LocalDate(2026, 3, 5)

    private fun useCase(
        occurrences: RecordingCycles,
        invoices: CountingInvoices,
        template: Recurring = this.template,
    ) = ConfirmRecurringUseCaseImpl(
        recurringRepository = FakeRecurringRepository(stored = listOf(template)),
        recurringOccurrenceRepository = occurrences,
        getOrCreateInvoiceForMonthUseCase = invoices,
        accountRepository = FakeAccountRepository(listOf(account, cardAccount)),
        clock = StoppedClock(),
    )

    @Test
    fun `a negative cycle amount is refused and nothing is written`() = runTest {
        val occurrences = RecordingCycles()

        val result = useCase(occurrences, CountingInvoices())(
            recurring = template,
            date = date,
            amount = -40.0,
        )

        val error = assertIs<Either.Left<Throwable>>(result).value
        assertIs<RecurringException>(error)
        assertEquals(RecurringError.AMOUNT_NOT_POSITIVE, error.error)
        assertNull(occurrences.recorded)
    }

    @Test
    fun `zero is refused by the same rule`() = runTest {
        val occurrences = RecordingCycles()

        val result = useCase(occurrences, CountingInvoices())(
            recurring = template,
            date = date,
            amount = 0.0,
        )

        val error = assertIs<Either.Left<Throwable>>(result).value
        assertIs<RecurringException>(error)
        assertEquals(RecurringError.AMOUNT_NOT_POSITIVE, error.error)
        assertNull(occurrences.recorded)
    }

    /**
     * The template's own amount is what an omitted override asks for, so a template that
     * was let through before the rule existed is refused here too — the cycle is where it
     * would have reached the ledger.
     */
    @Test
    fun `a template holding a negative amount is refused when its cycle is confirmed`() = runTest {
        val occurrences = RecordingCycles()
        val negative = template.copy(amount = -100.0)

        val result = useCase(occurrences, CountingInvoices(), template = negative)(
            recurring = negative,
            date = date,
        )

        val error = assertIs<Either.Left<Throwable>>(result).value
        assertIs<RecurringException>(error)
        assertEquals(RecurringError.AMOUNT_NOT_POSITIVE, error.error)
        assertNull(occurrences.recorded)
    }

    @Test
    fun `a cycle aimed at a card is refused before an invoice is opened for it`() = runTest {
        val occurrences = RecordingCycles()
        val invoices = CountingInvoices()

        val result = useCase(occurrences, invoices)(
            recurring = template,
            date = date,
            amount = -40.0,
            target = TransactionTarget.CREDIT_CARD,
            creditCard = card,
        )

        assertTrue(result.isLeft())
        assertEquals(0, invoices.calls, "an invoice opened here would outlive the refusal")
        assertNull(occurrences.recorded)
    }

    @Test
    fun `a positive cycle amount still goes through`() = runTest {
        val occurrences = RecordingCycles()

        val result = useCase(occurrences, CountingInvoices())(
            recurring = template,
            date = date,
            amount = 40.0,
        )

        assertTrue(result.isRight())
        assertEquals(40.0, occurrences.recorded?.legs?.single()?.amount)
    }
}

private class RecordingCycles : IRecurringOccurrenceRepository {
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

/** Counts what a refusal is supposed to have prevented: opening an invoice for the cycle. */
private class CountingInvoices : GetOrCreateInvoiceForMonthUseCase {
    var calls = 0

    override suspend fun invoke(
        creditCardId: Long,
        targetDueMonth: YearMonth,
    ): Either<Throwable, Invoice> {
        calls++
        throw NotImplementedError("no test here expects to reach an invoice")
    }
}
