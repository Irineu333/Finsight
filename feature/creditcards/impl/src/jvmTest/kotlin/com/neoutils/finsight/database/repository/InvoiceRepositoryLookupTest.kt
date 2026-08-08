package com.neoutils.finsight.database.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.mapper.CreditCardMapper
import com.neoutils.finsight.database.mapper.InvoiceMapper
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

/**
 * Looking up an invoice that is not there answers "not there". It used to answer
 * nothing at all: the observer mapped a missing row to an empty inner flow, and since
 * Room's query flow never completes, `first()` on it suspended forever instead of
 * returning — every `ensureNotNull(invoice)` downstream was unreachable, and the
 * caller's coroutine hung with no error and no crash report.
 *
 * The test timeout is the assertion: without it a regression makes this test hang
 * rather than fail.
 */
class InvoiceRepositoryLookupTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    @AfterTest fun tearDown() = db.close()

    private val creditCardRepository = CreditCardRepository(
        database = db,
        dao = db.creditCardDao(),
        accountDao = db.accountDao(),
        mapper = CreditCardMapper(),
        baseCurrencyRepository = FixedBaseCurrency("BRL"),
    )

    private val repository = InvoiceRepository(
        database = db,
        dao = db.invoiceDao(),
        dimensionDao = db.dimensionDao(),
        creditCardRepository = creditCardRepository,
        mapper = InvoiceMapper(),
    )

    private suspend fun insertCard() = creditCardRepository.insert(
        CreditCard(name = "Card", limit = 1_000.0, closingDay = 20, dueDay = 28),
        currency = "BRL",
    ).let { creditCardRepository.getCreditCardById(it)!! }

    private suspend fun insertInvoice(creditCard: CreditCard) = repository.insert(
        Invoice(
            creditCard = creditCard,
            openingMonth = YearMonth(2026, 1),
            closingMonth = YearMonth(2026, 2),
            dueMonth = YearMonth(2026, 2),
            status = Invoice.Status.OPEN,
        ),
    )

    @Test
    fun `getInvoiceById returns null for an unknown id instead of suspending`() = runTest(timeout = SUSPENSION_TIMEOUT) {
        assertNull(repository.getInvoiceById(id = 404L))
    }

    @Test
    fun `observeInvoiceById emits null for an unknown id instead of never emitting`() = runTest(timeout = SUSPENSION_TIMEOUT) {
        assertNull(repository.observeInvoiceById(invoiceId = 404L).first())
    }

    @Test
    fun `getInvoiceById resolves an existing invoice with its card`() = runTest(timeout = SUSPENSION_TIMEOUT) {
        val creditCard = insertCard()
        val inserted = insertInvoice(creditCard)

        val invoice = repository.getInvoiceById(inserted.id)

        assertEquals(inserted.id, invoice?.id)
        assertEquals(creditCard.id, invoice?.creditCard?.id)
    }

    /**
     * The invoice `insert` hands back is the one that was persisted, dimension
     * included. Rebuilding it from the bare row id used to drop `dimensionId`, and
     * every leg a caller tagged with it — each installment share past the first —
     * landed on no invoice at all, leaving the new invoices empty.
     */
    @Test
    fun `insert returns the invoice with the dimension it was born with`() = runTest(timeout = SUSPENSION_TIMEOUT) {
        val creditCard = insertCard()

        val inserted = insertInvoice(creditCard)

        assertNotNull(inserted.dimensionId)
        assertEquals(repository.getInvoiceById(inserted.id)?.dimensionId, inserted.dimensionId)
    }

    /**
     * An archived card keeps its invoices, and `getCreditCardById` resolves closed
     * cards — so the lookup must not go blank the moment the card is archived.
     */
    @Test
    fun `getInvoiceById still resolves an invoice of an archived card`() = runTest(timeout = SUSPENSION_TIMEOUT) {
        val creditCard = insertCard()
        val inserted = insertInvoice(creditCard)
        db.accountDao().close(creditCard.accountId)

        val invoice = repository.getInvoiceById(inserted.id)

        assertEquals(inserted.id, invoice?.id)
    }

    private companion object {
        val SUSPENSION_TIMEOUT = 10.seconds
    }
}
