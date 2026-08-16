@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.error.CreditCardError
import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.exception.CreditCardException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.usecase.HarvestExchangeRateUseCase
import com.neoutils.finsight.testing.NoExchangeRates
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
import kotlin.time.ExperimentalTime

/**
 * The card and invoice use cases are identified by **id**, and that form is the one
 * that carries the rule.
 *
 * Two properties are asserted of each, and they are the pair that makes the second form
 * safe to offer: an identity matching nothing is refused with a typed error before
 * anything is written, and the two forms of one use case produce the same result for
 * the same identity — because the one taking the aggregate only extracts its id.
 *
 * Resolving at execution rather than trusting what the caller holds is what the last
 * test is about: an invoice a screen loaded is a reading that can already be out of
 * date, and the operation has to read it as it is when the action runs.
 */
class CreditCardUseCaseIdentityTest {

    private val card = testCard()
    private val invoice = testInvoice(openingMonth = YearMonth(2026, 1))
    private val payingAccount = Account(id = 42, name = "Wallet", type = AccountType.ASSET, currency = "BRL")
    private val cardAccount =
        Account(id = card.accountId, name = "Card", type = AccountType.LIABILITY, currency = "BRL")
    private val date = LocalDate(2026, 1, 20)

    private fun accounts() = KnownAccounts(payingAccount, cardAccount)

    // --- Card ---

    @Test
    fun `deleting a card that does not exist is refused and nothing is removed`() = runTest {
        val cards = IdentityCardStore(card)
        val useCase = DeleteCreditCardUseCaseImpl(
            creditCardRepository = cards,
            entryRepository = FakeEntryRepository(InvoiceLedgerStore(card)),
            recurringRepository = NoRecurringTemplates,
        )

        val error = assertIs<CreditCardException>(useCase(404L).leftOrNull())

        assertEquals(CreditCardError.NOT_FOUND, error.error)
        assertTrue(cards.deleted.isEmpty(), "nothing may be removed")
    }

    @Test
    fun `deleting by id and by card are the same operation`() = runTest {
        val byId = IdentityCardStore(card)
        val byCard = IdentityCardStore(card)

        val fromId = DeleteCreditCardUseCaseImpl(
            creditCardRepository = byId,
            entryRepository = FakeEntryRepository(InvoiceLedgerStore(card)),
            recurringRepository = NoRecurringTemplates,
        )(card.id)

        val fromCard = DeleteCreditCardUseCaseImpl(
            creditCardRepository = byCard,
            entryRepository = FakeEntryRepository(InvoiceLedgerStore(card)),
            recurringRepository = NoRecurringTemplates,
        )(card)

        assertEquals(fromId.isRight(), fromCard.isRight())
        assertEquals(byId.deleted, byCard.deleted)
    }

    // --- Invoice lifecycle ---

    @Test
    fun `creating an invoice for a card that does not exist is refused`() = runTest {
        val store = RecordingInvoiceStore(invoice)
        val useCase = CreateInvoiceUseCaseImpl(IdentityCardStore(card), store)

        val error = assertIs<InvoiceException>(useCase(404L, YearMonth(2026, 5)).leftOrNull())

        assertEquals(InvoiceError.CreditCardNotFound, error.error)
        assertTrue(store.inserts.isEmpty(), "no invoice may be written")
    }

    @Test
    fun `creating by card id and by card produce the same invoice`() = runTest {
        val byIdStore = RecordingInvoiceStore(invoice)
        val byCardStore = RecordingInvoiceStore(invoice)

        val fromId = CreateInvoiceUseCaseImpl(IdentityCardStore(card), byIdStore)(
            card.id,
            YearMonth(2026, 5),
        ).getOrNull()
        val fromCard = CreateInvoiceUseCaseImpl(IdentityCardStore(card), byCardStore)(
            card,
            YearMonth(2026, 5),
        ).getOrNull()

        assertEquals(fromId?.openingMonth, fromCard?.openingMonth)
        assertEquals(fromId?.closingMonth, fromCard?.closingMonth)
        assertEquals(fromId?.dueMonth, fromCard?.dueMonth)
        assertEquals(fromId?.status, fromCard?.status)
    }

    @Test
    fun `resolving a month on a card that does not exist is refused`() = runTest {
        val store = RecordingInvoiceStore(invoice)
        val useCase = GetOrCreateInvoiceForMonthUseCaseImpl(
            creditCardRepository = IdentityCardStore(card),
            invoiceRepository = store,
            createInvoiceUseCase = CreateInvoiceUseCaseImpl(IdentityCardStore(card), store),
        )

        val error = assertIs<InvoiceException>(useCase(404L, YearMonth(2026, 5)).leftOrNull())

        assertEquals(InvoiceError.CreditCardNotFound, error.error)
        assertTrue(store.inserts.isEmpty(), "no invoice may be written")
    }

    @Test
    fun `resolving by card id and by card give the same invoice`() = runTest {
        val byIdStore = RecordingInvoiceStore(invoice)
        val byCardStore = RecordingInvoiceStore(invoice)

        fun useCase(store: RecordingInvoiceStore) = GetOrCreateInvoiceForMonthUseCaseImpl(
            creditCardRepository = IdentityCardStore(card),
            invoiceRepository = store,
            createInvoiceUseCase = CreateInvoiceUseCaseImpl(IdentityCardStore(card), store),
        )

        val fromId = useCase(byIdStore)(card.id, invoice.dueMonth).getOrNull()
        val fromCard = useCase(byCardStore)(card, invoice.dueMonth).getOrNull()

        assertEquals(fromId, fromCard)
        assertEquals(invoice.id, fromCard?.id)
    }

    @Test
    fun `opening on a card that does not exist is refused and nothing is written`() = runTest {
        val store = RecordingInvoiceStore()
        val useCase = OpenInvoiceUseCaseImpl(
            invoiceRepository = store,
            creditCardRepository = IdentityCardStore(card),
            clock = StoppedClock(date),
        )

        val error = assertIs<InvoiceException>(useCase(404L, YearMonth(2026, 1)).leftOrNull())

        assertEquals(InvoiceError.CreditCardNotFound, error.error)
        assertTrue(store.inserts.isEmpty(), "no invoice may be opened")
    }

    @Test
    fun `paying by invoice id and by invoice are the same operation`() = runTest {
        val closed = testInvoice(openingMonth = YearMonth(2026, 1), status = Invoice.Status.CLOSED)
        val byId = RecordingInvoiceStore(closed)
        val byInvoice = RecordingInvoiceStore(closed)
        val paidAt = LocalDate(2026, 2, 10)

        val fromId = PayInvoiceUseCaseImpl(byId, StoppedClock(LocalDate(2026, 2, 20)))(
            closed.id,
            paidAt,
        )
        val fromInvoice = PayInvoiceUseCaseImpl(byInvoice, StoppedClock(LocalDate(2026, 2, 20)))(
            closed,
            paidAt,
        )

        assertEquals(fromId.getOrNull(), fromInvoice.getOrNull())
        assertEquals(byId.updates, byInvoice.updates)
    }

    // --- Adjustment ---

    @Test
    fun `adjusting an invoice that does not exist is refused and nothing is written`() = runTest {
        val ledger = InvoiceLedgerStore(card)
        val useCase = AdjustInvoiceUseCaseImpl(
            invoiceRepository = RecordingInvoiceStore(invoice),
            transactionRepository = FakeTransactionRepository(ledger),
            calculateInvoiceUseCase = CalculateInvoiceUseCaseImpl(FakeEntryRepository(ledger)),
        )

        val error = assertIs<InvoiceException>(
            useCase(invoiceId = 404L, target = 100.0, adjustmentDate = date).leftOrNull()
        )

        assertEquals(InvoiceError.NotFound, error.error)
        assertTrue(ledger.entriesByTransaction.isEmpty(), "nothing may be written")
    }

    @Test
    fun `adjusting by id and by invoice are the same operation`() = runTest {
        val byIdLedger = InvoiceLedgerStore(card)
        val byInvoiceLedger = InvoiceLedgerStore(card)

        AdjustInvoiceUseCaseImpl(
            invoiceRepository = RecordingInvoiceStore(invoice),
            transactionRepository = FakeTransactionRepository(byIdLedger),
            calculateInvoiceUseCase = CalculateInvoiceUseCaseImpl(FakeEntryRepository(byIdLedger)),
        )(invoiceId = invoice.id, target = 100.0, adjustmentDate = date)

        AdjustInvoiceUseCaseImpl(
            invoiceRepository = RecordingInvoiceStore(invoice),
            transactionRepository = FakeTransactionRepository(byInvoiceLedger),
            calculateInvoiceUseCase = CalculateInvoiceUseCaseImpl(FakeEntryRepository(byInvoiceLedger)),
        )(invoice = invoice, target = 100.0, adjustmentDate = date)

        assertEquals(
            byIdLedger.adjustmentsByDate(invoice.dimensionId!!),
            byInvoiceLedger.adjustmentsByDate(invoice.dimensionId!!),
        )
        assertEquals(100.0, byIdLedger.dimensionOwed(invoice.dimensionId!!))
    }

    /**
     * The caller's copy says the invoice is open; the stored one has since been paid.
     * Resolving at execution is what makes the guard read the second.
     */
    @Test
    fun `the invoice is read as it is when the action runs, not as the caller holds it`() = runTest {
        val stale = testInvoice(openingMonth = YearMonth(2026, 1), status = Invoice.Status.CLOSED)
        val store = RecordingInvoiceStore(stale.copy(status = Invoice.Status.PAID))

        val error = assertIs<InvoiceException>(
            PayInvoiceUseCaseImpl(store, StoppedClock(LocalDate(2026, 2, 20)))(
                stale,
                LocalDate(2026, 2, 10),
            ).leftOrNull()
        )

        assertEquals(InvoiceError.CannotPayOpenInvoice, error.error)
        assertTrue(store.updates.isEmpty(), "a settled invoice is not settled twice")
    }

    // --- Payments: the paying account is an identity too (2.10) ---

    @Test
    fun `paying an invoice from an account that does not exist is refused`() = runTest {
        val closed = testInvoice(openingMonth = YearMonth(2026, 1), status = Invoice.Status.CLOSED)
        val store = RecordingInvoiceStore(closed)
        val writer = RecordingTransactionWriter()

        val error = assertIs<AccountException>(
            payInvoicePayment(store, writer)(
                invoiceId = closed.id,
                date = LocalDate(2026, 2, 10),
                accountId = 404L,
            ).leftOrNull()
        )

        assertEquals(AccountError.NOT_FOUND, error.error)
        assertNull(writer.captured, "no payment may be written")
        assertTrue(store.updates.isEmpty(), "and the invoice stays unpaid")
    }

    @Test
    fun `paying by account id and by account are the same operation`() = runTest {
        val closed = testInvoice(openingMonth = YearMonth(2026, 1), status = Invoice.Status.CLOSED)
        val byIdStore = RecordingInvoiceStore(closed)
        val byIdWriter = RecordingTransactionWriter()
        val byAccountStore = RecordingInvoiceStore(closed)
        val byAccountWriter = RecordingTransactionWriter()
        val paidAt = LocalDate(2026, 2, 10)

        val fromId = payInvoicePayment(byIdStore, byIdWriter)(
            invoiceId = closed.id,
            date = paidAt,
            accountId = payingAccount.id,
        )
        val fromAccount = payInvoicePayment(byAccountStore, byAccountWriter)(
            invoiceId = closed.id,
            date = paidAt,
            account = payingAccount,
        )

        assertEquals(fromId.getOrNull(), fromAccount.getOrNull())
        assertEquals(byIdWriter.captured?.legs, byAccountWriter.captured?.legs)
    }

    @Test
    fun `advancing a payment from an account that does not exist is refused`() = runTest {
        val store = RecordingInvoiceStore(invoice)
        val writer = RecordingTransactionWriter()

        val error = assertIs<AccountException>(
            advancePayment(store, writer)(
                invoiceId = invoice.id,
                amount = 10.0,
                date = date,
                accountId = 404L,
            ).leftOrNull()
        )

        assertEquals(AccountError.NOT_FOUND, error.error)
        assertNull(writer.captured, "no payment may be written")
    }

    @Test
    fun `advancing by account id and by account are the same operation`() = runTest {
        val byIdWriter = RecordingTransactionWriter()
        val byAccountWriter = RecordingTransactionWriter()

        val fromId = advancePayment(RecordingInvoiceStore(invoice), byIdWriter)(
            invoiceId = invoice.id,
            amount = 10.0,
            date = date,
            accountId = payingAccount.id,
        )
        val fromAccount = advancePayment(RecordingInvoiceStore(invoice), byAccountWriter)(
            invoiceId = invoice.id,
            amount = 10.0,
            date = date,
            account = payingAccount,
        )

        assertEquals(fromId.isRight(), fromAccount.isRight())
        assertEquals(byIdWriter.captured?.legs, byAccountWriter.captured?.legs)
    }

    private fun payInvoicePayment(
        store: RecordingInvoiceStore,
        writer: RecordingTransactionWriter,
    ) = PayInvoicePaymentUseCaseImpl(
        transactionRepository = writer,
        invoiceRepository = store,
        calculateInvoiceUseCase = OwesFixed(70.0),
        payInvoiceUseCase = PayInvoiceUseCaseImpl(store, StoppedClock(LocalDate(2026, 2, 20))),
        harvestExchangeRate = HarvestExchangeRateUseCase(NoExchangeRates),
        accountRepository = accounts(),
    )

    private fun advancePayment(
        store: RecordingInvoiceStore,
        writer: RecordingTransactionWriter,
    ) = AdvanceInvoicePaymentUseCaseImpl(
        transactionRepository = writer,
        invoiceRepository = store,
        calculateInvoiceUseCase = OwesFixed(70.0),
        harvestExchangeRate = HarvestExchangeRateUseCase(NoExchangeRates),
        accountRepository = accounts(),
        clock = StoppedClock(date),
    )
}

/** Every invoice owes the same, so the payment guards are the only thing under test. */
private class OwesFixed(private val owed: Double) : CalculateInvoiceUseCase {
    override suspend fun invoke(invoices: Collection<Invoice>): Map<Long, Double> =
        invoices.associate { it.id to owed }
}

/** Answers only the accounts it holds — an unknown id is genuinely absent. */
private class KnownAccounts(private vararg val accounts: Account) : IAccountRepository {
    override suspend fun getAccountById(accountId: Long): Account? =
        accounts.firstOrNull { it.id == accountId }

    override fun observeAccountById(accountId: Long): Flow<Account?> = throw NotImplementedError()
    override fun observeAllAccounts(): Flow<List<Account>> = throw NotImplementedError()
    override suspend fun getAllAccounts(): List<Account> = accounts.toList()
    override suspend fun getAllAccountsIncludingClosed(): List<Account> = accounts.toList()
    override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = throw NotImplementedError()
    override suspend fun getAllLedgerAccounts(): List<Account> = throw NotImplementedError()
    override fun observeAllLedgerAccounts(): Flow<List<Account>> = throw NotImplementedError()
    override suspend fun getDefaultAccount(): Account? = throw NotImplementedError()
    override fun observeDefaultAccount(): Flow<Account?> = throw NotImplementedError()
    override suspend fun hasYieldingAccount(): Boolean = false
    override suspend fun getAccountCount(): Int = throw NotImplementedError()
    override suspend fun insert(account: Account): Long = throw NotImplementedError()
    override suspend fun update(account: Account) = throw NotImplementedError()
    override suspend fun delete(account: Account) = throw NotImplementedError()
    override suspend fun reopen(accountId: Long) = throw NotImplementedError()
}

/** Resolves cards by id and remembers what was removed, so absence can be asserted. */
private class IdentityCardStore(private vararg val cards: CreditCard) : ICreditCardRepository {
    val deleted = mutableListOf<CreditCard>()
    val unarchived = mutableListOf<Long>()

    override suspend fun getCreditCardById(creditCardId: Long): CreditCard? =
        cards.firstOrNull { it.id == creditCardId }

    override suspend fun getAllCreditCards(): List<CreditCard> = cards.toList()
    override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard> = cards.toList()
    override suspend fun delete(creditCard: CreditCard) { deleted += creditCard }
    override suspend fun unarchive(accountId: Long) { unarchived += accountId }
    override fun observeAllCreditCards(): Flow<List<CreditCard>> = throw NotImplementedError()
    override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>> = throw NotImplementedError()
    override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> = throw NotImplementedError()
    override suspend fun insert(creditCard: CreditCard, currency: String): Long = throw NotImplementedError()
    override suspend fun update(creditCard: CreditCard) = throw NotImplementedError()
    override suspend fun currencyForNewCard(): String = throw NotImplementedError()
}

/** No template points at anything, so the retirement guards never fire. */
private object NoRecurringTemplates : IRecurringRepository {
    override suspend fun hasRecurringForCreditCard(creditCardId: Long): Boolean = false
    override suspend fun hasRecurringForAccount(accountId: Long): Boolean = false
    override suspend fun hasRecurringForCategory(categoryId: Long): Boolean = false
    override suspend fun hasTransactionForRecurring(recurringId: Long): Boolean = false
    override fun observeAllRecurring(): Flow<List<Recurring>> = flowOf(emptyList())
    override fun observeRecurringById(id: Long): Flow<Recurring?> = throw NotImplementedError()
    override suspend fun getRecurringById(id: Long): Recurring? = null
    override suspend fun insert(recurring: Recurring) = throw NotImplementedError()
    override suspend fun createWithFirstCycle(
        recurring: Recurring,
        firstCycle: TransactionIntent,
        occurrence: RecurringOccurrence,
    ): Transaction = throw NotImplementedError()
    override suspend fun update(recurring: Recurring) = throw NotImplementedError()
    override suspend fun delete(recurring: Recurring) = throw NotImplementedError()
}
