package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.RecurringError
import com.neoutils.finsight.domain.exception.RecurringException
import arrow.core.Either
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import com.neoutils.finsight.recurring
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A template's amount is stated in the currency of what it names (design D17), and confirming
 * it somewhere else is **refused rather than converted**.
 *
 * This is the one case that produced wrong data in silence outside the ledger: confirmation
 * lets the user redirect the account, and the amount travels with them, so a template written
 * against a real account posted its number as dollars the moment it was pointed at a dollar
 * one. Converting instead would pick a rate on the user's behalf, in the middle of a
 * confirmation they did not ask about.
 *
 * The modal offers only accounts of the template's own currency, so this refusal is the net and
 * never the designed path (design D26) — which is exactly why it has to exist here.
 */
class ConfirmRecurringCurrencyTest {

    private val date = LocalDate.parse("2026-05-10")

    private val local = Account(id = 1, name = "Nubank", type = AccountType.ASSET, currency = "BRL")
    private val foreign = Account(id = 2, name = "Chase", type = AccountType.ASSET, currency = "USD")

    @Test
    fun `confirming into an account of another currency is refused`() = runTest {
        val occurrences = RecordingOccurrences()

        val result = useCase(occurrences)(
            recurring = recurring().copy(account = local),
            date = date,
            account = foreign,
        )

        val error = assertIs<RecurringException>(result.leftOrNull())
        assertEquals(RecurringError.CURRENCY_MISMATCH, error.error)
        assertTrue(occurrences.confirmed.isEmpty(), "nothing may be written")
    }

    @Test
    fun `confirming into an account of the same currency is allowed`() = runTest {
        val occurrences = RecordingOccurrences()

        val result = useCase(occurrences)(
            recurring = recurring().copy(account = local),
            date = date,
            account = local.copy(id = 3, name = "Itaú"),
        )

        assertTrue(result.isRight())
        assertEquals(1, occurrences.confirmed.size)
    }

    private fun useCase(occurrences: IRecurringOccurrenceRepository) = ConfirmRecurringUseCase(
        recurringOccurrenceRepository = occurrences,
        getOrCreateInvoiceForMonthUseCase = throwingInvoices(),
    )

    /**
     * Never reached: both cases confirm into an account, and the card path is the one that
     * needs an invoice. Refusing to answer is what says so.
     */
    private fun throwingInvoices() = object : GetOrCreateInvoiceForMonthUseCase {
        override suspend fun invoke(
            creditCard: CreditCard,
            targetDueMonth: YearMonth,
        ): Either<Throwable, Invoice> = throw NotImplementedError()
    }

    private class RecordingOccurrences : IRecurringOccurrenceRepository {
        val confirmed = mutableListOf<TransactionIntent>()

        override suspend fun confirmCycle(
            intent: TransactionIntent,
            occurrence: RecurringOccurrence,
        ): Transaction {
            confirmed += intent
            return Transaction(id = 1, title = intent.title, date = intent.date, entries = emptyList())
        }

        override fun observeAllOccurrences(): Flow<List<RecurringOccurrence>> = flowOf(emptyList())
        override suspend fun getAllOccurrences(): List<RecurringOccurrence> = emptyList()
        override suspend fun getOccurrenceBy(recurringId: Long, yearMonth: YearMonth): RecurringOccurrence? = null
        override suspend fun getOccurrenceBy(recurringId: Long, cycleNumber: Int): RecurringOccurrence? = null
        override suspend fun save(occurrence: RecurringOccurrence): Long = throw NotImplementedError()
    }
}
