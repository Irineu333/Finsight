package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.ui.screen.invoiceTransactions.FakeEntryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Available limit is the figure the E2E suite asserts six times over and no unit test
 * watched: `limit − Σ what is still owed`. The rules that make it more than a
 * subtraction are here — which invoices count, what a credit balance does, and what a
 * card with no limit reports.
 *
 * The plural form is the one that carries them, so it is the one exercised: asking for
 * one card is asking for a collection of one, and the two must not be able to disagree.
 */
class CalculateAvailableLimitUseCaseTest {

    private val card = testCard(limit = 1_000.0)

    private fun invoiceOn(month: Int, id: Long, status: Invoice.Status = Invoice.Status.OPEN) =
        testInvoice(id = id, openingMonth = YearMonth(2026, month), status = status, card = card)

    private fun useCase(
        invoices: List<Invoice>,
        owed: Map<Long, Double>,
        cards: List<CreditCard> = listOf(card),
    ) = CalculateAvailableLimitUseCaseImpl(
        creditCardRepository = CountingCardStore(cards),
        invoiceRepository = RecordingInvoiceStore(*invoices.toTypedArray()),
        calculateInvoiceUseCase = CalculateInvoiceUseCaseImpl(
            FakeEntryRepository(owedByInvoiceId = owed)
        ),
    )

    @Test
    fun `every unpaid invoice commits limit, not just the open one`() = runTest {
        // This is what an installment does: the purchase is spread over invoices, and
        // the whole of it is committed from the moment it is made. The E2E flow asserts
        // the consequence; the rule is here.
        val invoices = listOf(
            invoiceOn(1, id = 1, status = Invoice.Status.CLOSED),
            invoiceOn(2, id = 2),
            invoiceOn(3, id = 3, status = Invoice.Status.FUTURE),
        )
        val owed = mapOf(100L to 320.0, 200L to 320.0, 300L to 320.0)

        val limit = useCase(invoices, owed)(card)

        assertEquals(960.0, limit.committedAmount)
        assertEquals(40.0, limit.available)
    }

    @Test
    fun `what holds the limit is split by the cycle holding it, and the split is the whole of it`() =
        runTest {
            // The three are different facts about the user's money: 100.00 is due, 250.00 is
            // accruing now, and 600.00 is committed to cycles that have not opened. One total
            // cannot say which is which — and read as "what is owed" it overstates by 600.00.
            val invoices = listOf(
                invoiceOn(1, id = 1, status = Invoice.Status.CLOSED),
                invoiceOn(2, id = 2, status = Invoice.Status.OPEN),
                invoiceOn(3, id = 3, status = Invoice.Status.FUTURE),
                invoiceOn(4, id = 4, status = Invoice.Status.FUTURE),
            )
            val owed = mapOf(100L to 100.0, 200L to 250.0, 300L to 300.0, 400L to 300.0)

            val limit = useCase(invoices, owed)(card)

            assertEquals(100.0, limit.closedAmount, "what is actually due to pay")
            assertEquals(250.0, limit.openAmount, "what the current cycle has accrued")
            assertEquals(600.0, limit.futureAmount, "the two cycles not yet opened, together")
            assertEquals(950.0, limit.committedAmount, "and the total is exactly their sum")
            assertEquals(50.0, limit.available)
        }

    @Test
    fun `a card whose cycles are all in one state leaves the other two at zero`() = runTest {
        val invoices = listOf(invoiceOn(1, id = 1, status = Invoice.Status.FUTURE))
        val owed = mapOf(100L to 400.0)

        val limit = useCase(invoices, owed)(card)

        assertEquals(0.0, limit.openAmount, "no cycle is open, and absence reads as zero")
        assertEquals(0.0, limit.closedAmount)
        assertEquals(400.0, limit.futureAmount)
        assertEquals(400.0, limit.committedAmount)
    }

    @Test
    fun `a paid invoice gives its limit back`() = runTest {
        val invoices = listOf(
            invoiceOn(1, id = 1, status = Invoice.Status.PAID),
            invoiceOn(2, id = 2),
        )
        val owed = mapOf(100L to 500.0, 200L to 120.0)

        val limit = useCase(invoices, owed)(card)

        assertEquals(120.0, limit.committedAmount, "the settled 500.00 is no longer owed")
        assertEquals(880.0, limit.available)
    }

    @Test
    fun `an invoice in credit frees no extra limit`() = runTest {
        // An over-payment leaves an invoice owing less than nothing. Letting it net
        // against its neighbour would report more limit than the card grants, so each
        // invoice is floored at zero on its own — not the sum.
        val invoices = listOf(invoiceOn(1, id = 1), invoiceOn(2, id = 2))
        val owed = mapOf(100L to -200.0, 200L to 300.0)

        val limit = useCase(invoices, owed)(card)

        assertEquals(300.0, limit.committedAmount)
        assertEquals(700.0, limit.available)
    }

    @Test
    fun `owing more than the limit reports no limit left, never a negative one`() = runTest {
        val invoices = listOf(invoiceOn(1, id = 1))
        val owed = mapOf(100L to 1_500.0)

        val limit = useCase(invoices, owed)(card)

        assertEquals(1_500.0, limit.committedAmount, "what is owed is reported as it is")
        assertEquals(0.0, limit.available)
        assertEquals(1.0, limit.usage, "and usage stops at full, so no bar overflows")
    }

    @Test
    fun `a card with no limit reports no usage instead of dividing by zero`() = runTest {
        val noLimit = testCard(limit = 0.0)
        val invoices = listOf(invoiceOn(1, id = 1, status = Invoice.Status.OPEN))
        val owed = mapOf(100L to 250.0)

        val limit = useCase(invoices, owed, cards = listOf(noLimit))(noLimit)

        assertEquals(250.0, limit.committedAmount)
        assertEquals(0.0, limit.available)
        assertEquals(0.0, limit.usage)
    }

    @Test
    fun `a card with nothing on it has all of its limit`() = runTest {
        val limit = useCase(invoices = emptyList(), owed = emptyMap())(card)

        assertEquals(0.0, limit.committedAmount)
        assertEquals(1_000.0, limit.available)
        assertEquals(0.0, limit.usage)
    }

    // --- The plural form (design D7) ---

    @Test
    fun `N cards cost one read of each kind, not N`() = runTest {
        val cards = (1L..4L).map { id ->
            CreditCard(
                id = id,
                name = "Card $id",
                limit = 1_000.0,
                closingDay = 5,
                dueDay = 15,
                accountId = 10 + id,
            )
        }
        val invoices = cards.map { card ->
            testInvoice(id = card.id, openingMonth = YearMonth(2026, 1), card = card)
        }
        val cardStore = CountingCardStore(cards)
        val invoiceStore = CountingInvoiceStore(invoices)
        val entryRepository = CountingEntryRepository(
            invoices.associate { it.dimensionId!! to 100.0 }
        )

        val limits = CalculateAvailableLimitUseCaseImpl(
            creditCardRepository = cardStore,
            invoiceRepository = invoiceStore,
            calculateInvoiceUseCase = CalculateInvoiceUseCaseImpl(entryRepository),
        )(cards.map { it.id })

        assertEquals(4, limits.size)
        assertEquals(1, cardStore.listings, "the cards are read once")
        assertEquals(1, invoiceStore.batchedReads, "the invoices are read once")
        assertEquals(0, invoiceStore.perCardReads, "and never card by card")
        assertEquals(1, entryRepository.batchedReads, "the ledger is read once")
        assertEquals(0, entryRepository.perDimensionReads, "and never invoice by invoice")
    }

    @Test
    fun `an identity with no card is absent from the map, and reads as the neutral limit`() = runTest {
        val store = CountingCardStore(listOf(card))

        val limits = CalculateAvailableLimitUseCaseImpl(
            creditCardRepository = store,
            invoiceRepository = CountingInvoiceStore(emptyList()),
            calculateInvoiceUseCase = CalculateInvoiceUseCaseImpl(CountingEntryRepository(emptyMap())),
        )(listOf(card.id, 404L))

        assertTrue(card.id in limits, "the card that exists is answered")
        assertFalse(404L in limits, "the one that does not is absent, not zero-filled")
        assertEquals(Limit.NONE, limits[404L] ?: Limit.NONE)
    }

    @Test
    fun `asking for one card by id, by card and in a collection gives the same figure`() = runTest {
        val invoices = listOf(invoiceOn(1, id = 1))
        val owed = mapOf(100L to 400.0)

        val fromCollection = useCase(invoices, owed)(listOf(card.id))[card.id]
        val fromId = useCase(invoices, owed)(card.id)
        val fromCard = useCase(invoices, owed)(card)

        assertEquals(fromCollection, fromId)
        assertEquals(fromId, fromCard)
        assertEquals(600.0, fromCard.available)
    }

    @Test
    fun `an empty request reads nothing at all`() = runTest {
        val cardStore = CountingCardStore(listOf(card))
        val invoiceStore = CountingInvoiceStore(emptyList())

        val limits = CalculateAvailableLimitUseCaseImpl(
            creditCardRepository = cardStore,
            invoiceRepository = invoiceStore,
            calculateInvoiceUseCase = CalculateInvoiceUseCaseImpl(CountingEntryRepository(emptyMap())),
        )(emptyList())

        assertTrue(limits.isEmpty())
        assertEquals(0, cardStore.listings)
        assertEquals(0, invoiceStore.batchedReads)
    }
}

/** Answers the cards it holds, and counts how many times it was asked for the list. */
private class CountingCardStore(private val cards: List<CreditCard>) : ICreditCardRepository {
    var listings = 0
        private set

    override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard> {
        listings++
        return cards
    }

    override suspend fun getCreditCardById(creditCardId: Long): CreditCard? =
        cards.firstOrNull { it.id == creditCardId }

    override suspend fun getAllCreditCards(): List<CreditCard> = cards
    override fun observeAllCreditCards(): Flow<List<CreditCard>> = throw NotImplementedError()
    override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>> = throw NotImplementedError()
    override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> = throw NotImplementedError()
    override suspend fun insert(creditCard: CreditCard, currency: String): Long = throw NotImplementedError()
    override suspend fun update(creditCard: CreditCard) = throw NotImplementedError()
    override suspend fun delete(creditCard: CreditCard) = throw NotImplementedError()
    override suspend fun unarchive(accountId: Long) = throw NotImplementedError()
    override suspend fun currencyForNewCard(): String = throw NotImplementedError()
}

/**
 * Counts the two shapes of the unpaid-invoice read, so the N+1 cannot come back.
 *
 * "Unpaid" is the production predicate — `status NOT IN ('PAID', 'RETROACTIVE')`, stated in
 * `InvoiceDao` — so that the split under test is fed exactly the states the app feeds it.
 */
private class CountingInvoiceStore(private val invoices: List<Invoice>) : IInvoiceRepository {
    var batchedReads = 0
        private set
    var perCardReads = 0
        private set

    private val Invoice.isUnpaid get() = !status.isPaid && !status.isRetroactive

    override suspend fun getUnpaidInvoicesByCreditCards(
        creditCardIds: Collection<Long>,
    ): Map<Long, List<Invoice>> {
        batchedReads++
        return invoices
            .filter { it.creditCard.id in creditCardIds && it.isUnpaid }
            .groupBy { it.creditCard.id }
    }

    override suspend fun getUnpaidInvoicesByCreditCard(creditCardId: Long): List<Invoice> {
        perCardReads++
        return invoices.filter { it.creditCard.id == creditCardId && it.isUnpaid }
    }

    override suspend fun getInvoicesByCreditCard(creditCardId: Long): List<Invoice> =
        invoices.filter { it.creditCard.id == creditCardId }

    override suspend fun getAllInvoices(): List<Invoice> = invoices
    override suspend fun getInvoiceById(id: Long): Invoice? = invoices.firstOrNull { it.id == id }
    override suspend fun getOpenInvoice(creditCardId: Long): Invoice? = throw NotImplementedError()
    override fun observeAllInvoices(): Flow<List<Invoice>> = throw NotImplementedError()
    override fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>> = throw NotImplementedError()
    override fun observeInvoiceById(invoiceId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeOpenInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeAvailableInvoices(creditCardId: Long): Flow<List<Invoice>> = throw NotImplementedError()
    override fun observeUnpaidInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeUnpaidInvoices(): Flow<List<Invoice>> = throw NotImplementedError()
    override suspend fun insert(invoice: Invoice): Invoice = throw NotImplementedError()
    override suspend fun update(invoice: Invoice) = throw NotImplementedError()
    override suspend fun deleteById(id: Long) = throw NotImplementedError()
}

/** The same count over the ledger: one owed read for every dimension, not one each. */
private class CountingEntryRepository(
    private val owedByDimensionId: Map<Long, Double>,
) : IEntryRepository by FakeEntryRepository(owedByDimensionId) {
    var batchedReads = 0
        private set
    var perDimensionReads = 0
        private set

    override suspend fun owedByDimensionByCurrency(
        dimensionIds: Collection<Long>,
    ): Map<Long, MoneyByCurrency> {
        batchedReads++
        return dimensionIds.distinct().associateWith {
            MoneyByCurrency.of("BRL", owedByDimensionId[it] ?: 0.0)
        }
    }

    override suspend fun dimensionOwedByCurrency(dimensionId: Long): MoneyByCurrency {
        perDimensionReads++
        return MoneyByCurrency.of("BRL", owedByDimensionId[dimensionId] ?: 0.0)
    }
}
