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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

/**
 * What "still to settle" means as a query: not paid, and due no later than the month
 * asked about.
 *
 * The criterion is the negation of `PAID`, so `RETROACTIVE` — which
 * `observeUnpaidInvoices` leaves out — is inside this perimeter, and so is a `FUTURE`
 * invoice whose due month has already arrived. Both are debt that has not been settled
 * and whose settlement window is open, which is the only thing this read is about.
 */
class InvoiceSettlementPerimeterTest {

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

    private val march = YearMonth(2026, 3)

    private suspend fun insertCard(name: String = "Card") = creditCardRepository.insert(
        CreditCard(name = name, limit = 1_000.0, closingDay = 20, dueDay = 28),
        currency = "BRL",
    ).let { creditCardRepository.getCreditCardById(it)!! }

    // The due month is what this read cuts on, so it is the parameter; the window before
    // it only has to be a valid one.
    private suspend fun insertInvoice(
        creditCard: CreditCard,
        dueMonth: YearMonth,
        status: Invoice.Status,
    ) = repository.insert(
        Invoice(
            creditCard = creditCard,
            openingMonth = dueMonth.plus(-2, DateTimeUnit.MONTH),
            closingMonth = dueMonth.plus(-1, DateTimeUnit.MONTH),
            dueMonth = dueMonth,
            status = status,
        ),
    )

    private suspend fun toSettle() = repository.observeInvoicesToSettle(march).first()

    @Test
    fun `a retroactive invoice is inside the perimeter`() = runTest(timeout = TIMEOUT) {
        val card = insertCard()
        val retroactive = insertInvoice(card, march, Invoice.Status.RETROACTIVE)

        assertEquals(listOf(retroactive.id), toSettle().map { it.id })
    }

    @Test
    fun `a paid invoice is outside it`() = runTest(timeout = TIMEOUT) {
        val card = insertCard()
        insertInvoice(card, march, Invoice.Status.PAID)

        assertEquals(emptyList(), toSettle().map { it.id })
    }

    @Test
    fun `an invoice due next month is outside it`() = runTest(timeout = TIMEOUT) {
        val card = insertCard()
        insertInvoice(card, YearMonth(2026, 4), Invoice.Status.OPEN)

        assertEquals(emptyList(), toSettle().map { it.id })
    }

    @Test
    fun `an invoice overdue from an earlier month is inside it`() = runTest(timeout = TIMEOUT) {
        val card = insertCard()
        val overdue = insertInvoice(card, YearMonth(2026, 1), Invoice.Status.CLOSED)

        assertEquals(listOf(overdue.id), toSettle().map { it.id })
    }

    /**
     * A card left unclosed keeps posting into an invoice that is still `FUTURE` while
     * its due month goes by. The money is owed all the same.
     */
    @Test
    fun `a future invoice whose due month has arrived is inside it`() = runTest(timeout = TIMEOUT) {
        val card = insertCard()
        val stale = insertInvoice(card, march, Invoice.Status.FUTURE)

        assertEquals(listOf(stale.id), toSettle().map { it.id })
    }

    @Test
    fun `the perimeter spans every card and reads oldest first`() = runTest(timeout = TIMEOUT) {
        val first = insertCard(name = "First")
        val second = insertCard(name = "Second")
        val older = insertInvoice(first, YearMonth(2026, 1), Invoice.Status.CLOSED)
        val newer = insertInvoice(second, march, Invoice.Status.CLOSED)

        assertEquals(listOf(older.id, newer.id), toSettle().map { it.id })
    }
}

private val TIMEOUT = 10.seconds
