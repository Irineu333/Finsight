package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.FakeAccountRepository
import com.neoutils.finsight.FakeRecurringRepository
import com.neoutils.finsight.StoppedClock
import com.neoutils.finsight.domain.error.RecurringError
import com.neoutils.finsight.domain.exception.RecurringException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.RecurringSettledMoney
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
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
 * **A cycle is classified in the direction the money moved, and this is the only write
 * that has nowhere else to say so.**
 *
 * The rule is `isAccept`'s — a category classifies one direction only — and the five tools
 * that build a form already hold it. A confirmation builds none: the `category` override
 * reaches [contraLegFor] directly, and that function takes the *nature of the contra leg
 * from the category*. So an expense template confirmed under an income category posts
 * `{ASSET −, INCOME +}`: the money leaves the account and `deriveTransactionLabel` reads
 * the posting back as income.
 *
 * **What is asserted here is the ledger, not the refusal.** `Σ = 0` still holds with the
 * legs inverted, so a `Right` proves nothing and an assertion on the result would pass
 * against the bug. Every test below either finds nothing recorded or reads the contra
 * leg's own nature.
 *
 * The refusal also has to come **before the invoice is resolved**, for the reason the
 * amount rule already documents: resolving one creates it outside the unit of work
 * (design D7), so a later refusal leaves an invoice behind for a cycle that never posted.
 */
class ConfirmRecurringCoherenceTest {

    private val account = Account(id = 1, name = "Nubank", type = AccountType.ASSET, currency = "BRL")
    private val cardAccount =
        Account(id = 2, name = "Card", type = AccountType.LIABILITY, currency = "BRL")

    private val card = CreditCard(
        id = 10, name = "Nubank", limit = 1000.0, closingDay = 5, dueDay = 15,
        accountId = cardAccount.id,
    )

    private fun category(id: Long, name: String, type: Category.Type) = Category(
        id = id,
        name = name,
        icon = CategoryLazyIcon("tag"),
        type = type,
        createdAt = 0L,
        dimensionId = id * 100,
    )

    private val subscriptions = category(1, "Assinaturas", Category.Type.EXPENSE)
    private val salary = category(2, "Salário", Category.Type.INCOME)

    private val expenseTemplate = Recurring(
        id = 7, type = TransactionType.EXPENSE, amount = 39.90, title = "Netflix",
        dayOfMonth = 5, category = subscriptions, account = account, creditCard = null,
        createdAt = 0L,
    )

    private val incomeTemplate = Recurring(
        id = 8, type = TransactionType.INCOME, amount = 5_000.0, title = "Salário",
        dayOfMonth = 5, category = salary, account = account, creditCard = null,
        createdAt = 0L,
    )

    private val date = LocalDate(2026, 3, 5)

    private fun useCase(
        occurrences: RecordingIntent,
        invoices: UnopenedInvoices,
        template: Recurring = this.expenseTemplate,
    ) = ConfirmRecurringUseCaseImpl(
        recurringRepository = FakeRecurringRepository(stored = listOf(template)),
        recurringOccurrenceRepository = occurrences,
        getOrCreateInvoiceForMonthUseCase = invoices,
        accountRepository = FakeAccountRepository(listOf(account, cardAccount)),
        clock = StoppedClock(date.atStartOfDayIn(TimeZone.currentSystemDefault())),
    )

    @Test
    fun `an expense cycle classified under an income category is refused, and nothing is posted`() =
        runTest {
            val occurrences = RecordingIntent()

            val result = useCase(occurrences, UnopenedInvoices(), template = expenseTemplate)(
                recurring = expenseTemplate,
                date = date,
                category = salary,
            )

            val error = assertIs<Either.Left<Throwable>>(result).value
            assertIs<RecurringException>(error)
            assertEquals(RecurringError.CATEGORY_DIRECTION_MISMATCH, error.error)

            assertNull(
                occurrences.recorded,
                "the cycle reached the ledger with its contra leg on " +
                    "${occurrences.recorded?.contra?.nature}: the money left the account and the " +
                    "posting reads as income",
            )
        }

    @Test
    fun `an income cycle classified under an expense category is refused by the same rule`() =
        runTest {
            val occurrences = RecordingIntent()

            val result = useCase(occurrences, UnopenedInvoices(), template = incomeTemplate)(
                recurring = incomeTemplate,
                date = date,
                category = subscriptions,
            )

            val error = assertIs<Either.Left<Throwable>>(result).value
            assertIs<RecurringException>(error)
            assertEquals(RecurringError.CATEGORY_DIRECTION_MISMATCH, error.error)

            assertNull(
                occurrences.recorded,
                "the cycle reached the ledger with its contra leg on " +
                    "${occurrences.recorded?.contra?.nature}",
            )
        }

    /**
     * The same disagreement reached from the other side: no override is given, and the
     * caller passes the template's own category through — which is what both the sheet and
     * the tool do. A template stored incoherent before the rule existed is refused at the
     * cycle, because the cycle is where it would have reached the ledger.
     */
    @Test
    fun `a template stored incoherent is refused when its cycle is confirmed`() = runTest {
        val occurrences = RecordingIntent()
        val incoherent = expenseTemplate.copy(category = salary)

        val result = useCase(occurrences, UnopenedInvoices(), template = incoherent)(
            recurring = incoherent,
            date = date,
            category = incoherent.category,
        )

        val error = assertIs<Either.Left<Throwable>>(result).value
        assertIs<RecurringException>(error)
        assertEquals(RecurringError.CATEGORY_DIRECTION_MISMATCH, error.error)
        assertNull(occurrences.recorded)
    }

    /**
     * The refusal sits where the amount rule sits, and for the same reason: an invoice
     * opened for a cycle that is then refused outlives the refusal.
     */
    @Test
    fun `a cycle aimed at a card is refused before an invoice is opened for it`() = runTest {
        val occurrences = RecordingIntent()
        val invoices = UnopenedInvoices()

        val result = useCase(occurrences, invoices, template = expenseTemplate)(
            recurring = expenseTemplate,
            date = date,
            target = TransactionTarget.CREDIT_CARD,
            creditCard = card,
            category = salary,
        )

        assertTrue(result.isLeft())
        assertEquals(0, invoices.calls, "an invoice opened here would outlive the refusal")
        assertNull(occurrences.recorded)
    }

    // ------------------------------------------------------------------------------
    // What the rule may not cost: everything coherent still posts, on the right side
    // ------------------------------------------------------------------------------

    @Test
    fun `a coherent category still posts, on the nominal of its own nature`() = runTest {
        val occurrences = RecordingIntent()

        val result = useCase(occurrences, UnopenedInvoices(), template = expenseTemplate)(
            recurring = expenseTemplate,
            date = date,
            category = subscriptions,
        )

        assertTrue(result.isRight(), "a coherent classification was refused")
        assertEquals(AccountType.EXPENSE, occurrences.recorded?.contra?.nature)
        assertEquals(subscriptions.dimensionId, occurrences.recorded?.contra?.dimensionId)
    }

    @Test
    fun `an income cycle under an income category still posts on the income nominal`() = runTest {
        val occurrences = RecordingIntent()

        val result = useCase(occurrences, UnopenedInvoices(), template = incomeTemplate)(
            recurring = incomeTemplate,
            date = date,
            category = salary,
        )

        assertTrue(result.isRight(), "a coherent classification was refused")
        assertEquals(AccountType.INCOME, occurrences.recorded?.contra?.nature)
        assertEquals(salary.dimensionId, occurrences.recorded?.contra?.dimensionId)
    }

    /**
     * "No category" is the absence of a dimension, and the rule has nothing to say about
     * it: there is no direction to disagree with. The nature then comes from the leg's own
     * type, which is what `contraLegFor` already does.
     */
    @Test
    fun `a cycle with no category is not what this rule refuses`() = runTest {
        val occurrences = RecordingIntent()

        val result = useCase(occurrences, UnopenedInvoices(), template = expenseTemplate)(
            recurring = expenseTemplate,
            date = date,
            category = null,
        )

        assertTrue(result.isRight(), "an unclassified cycle was refused")
        assertEquals(AccountType.EXPENSE, occurrences.recorded?.contra?.nature)
        assertNull(occurrences.recorded?.contra?.dimensionId)
    }
}

/** Keeps the intent that reached the write, which is the only place the legs can be read. */
private class RecordingIntent : IRecurringOccurrenceRepository {
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

/** Counts what a refusal is supposed to have prevented: opening an invoice for the cycle. */
private class UnopenedInvoices : GetOrCreateInvoiceForMonthUseCase {
    var calls = 0

    override suspend fun invoke(
        creditCardId: Long,
        targetDueMonth: YearMonth,
    ): Either<Throwable, Invoice> {
        calls++
        throw NotImplementedError("no test here expects to reach an invoice")
    }
}
