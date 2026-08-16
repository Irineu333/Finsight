package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.FakeRecurringRepository
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
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **A confirmation may say what the cycle actually was, and only about that cycle.**
 *
 * Title and category are what a cycle diverges in when the month did not match the
 * template — the streaming charge that came under another name, the pharmacy run that
 * was a consultation. The overrides land on the transaction; the recurring is read and
 * never written, so the next cycle opens on the template again.
 */
class ConfirmRecurringOverridesTest {

    private val account = Account(id = 1, name = "Nubank", type = AccountType.ASSET, currency = "BRL")
    private val cardAccount =
        Account(id = 2, name = "Card", type = AccountType.LIABILITY, currency = "BRL")

    private val card = CreditCard(
        id = 10, name = "Nubank", limit = 1000.0, closingDay = 5, dueDay = 15,
        accountId = cardAccount.id,
    )

    private fun category(id: Long, name: String, dimensionId: Long) = Category(
        id = id,
        name = name,
        icon = CategoryLazyIcon("shopping"),
        type = Category.Type.EXPENSE,
        createdAt = 0L,
        dimensionId = dimensionId,
    )

    private val streaming = category(1, "Streaming", dimensionId = 100)
    private val health = category(2, "Saúde", dimensionId = 200)

    private val template = Recurring(
        id = 7, type = TransactionType.EXPENSE, amount = 100.0, title = "Netflix",
        dayOfMonth = 5, category = streaming, account = account, creditCard = null,
        createdAt = 0L,
    )

    private val invoice = Invoice(
        id = 5,
        creditCard = card,
        dimensionId = 300,
        openingMonth = YearMonth(2026, 2),
        closingMonth = YearMonth(2026, 3),
        dueMonth = YearMonth(2026, 3),
        status = Invoice.Status.OPEN,
    )

    private val date = LocalDate(2026, 3, 5)

    private fun useCase(occurrences: RecordingIntents) = ConfirmRecurringUseCaseImpl(
        recurringRepository = FakeRecurringRepository(stored = listOf(template)),
        recurringOccurrenceRepository = occurrences,
        getOrCreateInvoiceForMonthUseCase = object : GetOrCreateInvoiceForMonthUseCase {
            override suspend fun invoke(creditCardId: Long, targetDueMonth: YearMonth) =
                throw NotImplementedError("every card test here passes the invoice in")
        },
        accountRepository = StubAccounts(listOf(account, cardAccount)),
    )

    /**
     * What an omitted override means is decided inside the use case, and it is not one
     * rule but two: an omitted amount or destination asks for the cycle the template
     * describes, while an omitted title or category asks for nothing at all. Both are
     * things the user can erase, and substituting the template's back in would return
     * what they had just removed.
     */
    @Test
    fun `an omitted amount and destination come from the template, an omitted title and category do not`() =
        runTest {
            val occurrences = RecordingIntents()

            val result = useCase(occurrences)(recurring = template, date = date)

            assertTrue(result.isRight())
            assertEquals(template.amount, occurrences.recorded?.legs?.single()?.amount)
            assertEquals(account.id, occurrences.recorded?.legs?.single()?.accountId)
            assertNull(occurrences.recorded?.title)
            assertNull(occurrences.recorded?.contra?.dimensionId)
        }

    @Test
    fun `an overridden title and category reach the transaction`() = runTest {
        val occurrences = RecordingIntents()

        val result = useCase(occurrences)(
            recurring = template,
            date = date,
            title = "Consulta",
            category = health,
        )

        assertTrue(result.isRight())
        assertEquals("Consulta", occurrences.recorded?.title)
        assertEquals(health.dimensionId, occurrences.recorded?.contra?.dimensionId)
    }

    @Test
    fun `the overrides reach a confirmation aimed at a card just the same`() = runTest {
        val occurrences = RecordingIntents()

        val result = useCase(occurrences)(
            recurring = template,
            date = date,
            target = TransactionTarget.CREDIT_CARD,
            creditCard = card,
            invoice = invoice,
            title = "Consulta",
            category = health,
        )

        assertTrue(result.isRight())
        assertEquals("Consulta", occurrences.recorded?.title)
        assertEquals(health.dimensionId, occurrences.recorded?.contra?.dimensionId)
        assertEquals(
            invoice.dimensionId,
            occurrences.recorded?.legs?.single()?.dimensionId,
            "the invoice still lands on the liability leg — the category is the contra's",
        )
    }

    /**
     * "Uncategorized" is the absence of a dimension, not a bucket: clearing the selector
     * is a legitimate state and the confirmation goes through.
     */
    @Test
    fun `clearing the category writes a transaction with no dimension`() = runTest {
        val occurrences = RecordingIntents()

        val result = useCase(occurrences)(recurring = template, date = date, category = null)

        assertTrue(result.isRight())
        assertNull(occurrences.recorded?.contra?.dimensionId)
        assertEquals(AccountType.EXPENSE, occurrences.recorded?.contra?.nature)
    }

    /**
     * A cycle with no title of its own is displayed by its category — the app's one rule
     * for reading titles. Nothing here substitutes the template's title back in.
     */
    @Test
    fun `an absent title is written as an absence`() = runTest {
        val occurrences = RecordingIntents()

        val result = useCase(occurrences)(recurring = template, date = date, title = null)

        assertTrue(result.isRight())
        assertNull(occurrences.recorded?.title)
    }

    @Test
    fun `overriding a cycle leaves the template untouched`() = runTest {
        val occurrences = RecordingIntents()

        useCase(occurrences)(
            recurring = template,
            date = date,
            title = "Consulta",
            category = health,
        )

        assertEquals("Netflix", template.title, "the recurring is read, never written")
        assertEquals(streaming, template.category)
    }
}

private class RecordingIntents : IRecurringOccurrenceRepository {
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

private class StubAccounts(private val accounts: List<Account>) : IAccountRepository {
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
