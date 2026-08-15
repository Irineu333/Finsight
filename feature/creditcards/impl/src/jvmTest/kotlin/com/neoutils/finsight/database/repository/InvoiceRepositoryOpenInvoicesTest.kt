package com.neoutils.finsight.database.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.mapper.CreditCardMapper
import com.neoutils.finsight.database.mapper.InvoiceMapper
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The open invoices of every card, in one read. The strictly `OPEN` query existed
 * only scoped to a card: without one, a caller had to take the wider unpaid list —
 * which carries `CLOSED` and `FUTURE` — and filter it in memory.
 */
class InvoiceRepositoryOpenInvoicesTest {

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

    private suspend fun insertCard(name: String) = creditCardRepository.insert(
        CreditCard(name = name, limit = 1_000.0, closingDay = 20, dueDay = 28),
        currency = "BRL",
    ).let { creditCardRepository.getCreditCardById(it)!! }

    private suspend fun insertInvoice(
        creditCard: CreditCard,
        openingMonth: YearMonth,
        status: Invoice.Status,
    ) = repository.insert(
        Invoice(
            creditCard = creditCard,
            openingMonth = openingMonth,
            closingMonth = openingMonth.plus(1, DateTimeUnit.MONTH),
            dueMonth = openingMonth.plus(1, DateTimeUnit.MONTH),
            status = status,
        ),
    )

    @Test
    fun `no invoice at all answers an empty list`() = runTest {
        assertEquals(emptyList(), repository.getOpenInvoices())
    }

    @Test
    fun `only the strictly open invoices are returned`() = runTest {
        val creditCard = insertCard(name = "Card")
        val open = insertInvoice(creditCard, YearMonth(2026, 3), Invoice.Status.OPEN)
        insertInvoice(creditCard, YearMonth(2026, 2), Invoice.Status.CLOSED)
        insertInvoice(creditCard, YearMonth(2026, 1), Invoice.Status.PAID)
        insertInvoice(creditCard, YearMonth(2026, 4), Invoice.Status.FUTURE)
        insertInvoice(creditCard, YearMonth(2025, 12), Invoice.Status.RETROACTIVE)

        val invoices = repository.getOpenInvoices()

        assertEquals(listOf(open.id), invoices.map { it.id })
        assertEquals(creditCard.id, invoices.single().creditCard.id)
    }

    @Test
    fun `open invoices of every card come back, newest opening month first`() = runTest {
        val older = insertCard(name = "Older")
        val newer = insertCard(name = "Newer")
        val olderInvoice = insertInvoice(older, YearMonth(2026, 1), Invoice.Status.OPEN)
        val newerInvoice = insertInvoice(newer, YearMonth(2026, 5), Invoice.Status.OPEN)

        val invoices = repository.getOpenInvoices()

        assertEquals(listOf(newerInvoice.id, olderInvoice.id), invoices.map { it.id })
    }

    /**
     * An archived card keeps its invoices, so resolving the card of an open invoice
     * has to include the closed ones — otherwise the read blows up on the `!!`.
     */
    @Test
    fun `an open invoice of an archived card is still resolved`() = runTest {
        val creditCard = insertCard(name = "Archived")
        val invoice = insertInvoice(creditCard, YearMonth(2026, 3), Invoice.Status.OPEN)
        db.accountDao().close(creditCard.accountId)

        val invoices = repository.getOpenInvoices()

        assertTrue(invoices.any { it.id == invoice.id })
    }
}
