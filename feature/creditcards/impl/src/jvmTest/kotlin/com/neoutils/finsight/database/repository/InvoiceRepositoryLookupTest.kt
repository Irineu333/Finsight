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
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
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

    /** The base currency a real app resolves from the locale; here it is simply stated. */
    private val baseCurrency = object : IBaseCurrencyRepository {
        override fun observe(): StateFlow<String> = MutableStateFlow("BRL")
        override suspend fun set(currency: String) = Unit
    }


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
    )


    private val repository = InvoiceRepository(
        database = db,
        dao = db.invoiceDao(),
        dimensionDao = db.dimensionDao(),
        creditCardRepository = creditCardRepository,
        mapper = InvoiceMapper(),
    )

    private suspend fun insertCard() = creditCardRepository.insert(
        CreditCard(currency = "BRL", name = "Card", limit = 1_000.0, closingDay = 20, dueDay = 28),
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
        val invoiceId = insertInvoice(creditCard)

        val invoice = repository.getInvoiceById(invoiceId)

        assertEquals(invoiceId, invoice?.id)
        assertEquals(creditCard.id, invoice?.creditCard?.id)
    }

    /**
     * An archived card keeps its invoices, and `getCreditCardById` resolves closed
     * cards — so the lookup must not go blank the moment the card is archived.
     */
    @Test
    fun `getInvoiceById still resolves an invoice of an archived card`() = runTest(timeout = SUSPENSION_TIMEOUT) {
        val creditCard = insertCard()
        val invoiceId = insertInvoice(creditCard)
        db.accountDao().close(creditCard.accountId)

        val invoice = repository.getInvoiceById(invoiceId)

        assertEquals(invoiceId, invoice?.id)
    }

    private companion object {
        val SUSPENSION_TIMEOUT = 10.seconds
    }
}
