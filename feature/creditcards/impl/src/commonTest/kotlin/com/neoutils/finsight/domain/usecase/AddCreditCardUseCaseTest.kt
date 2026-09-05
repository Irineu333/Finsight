@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.form.CreditCardForm
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

/**
 * A card that exists without an open invoice accepts no expense: every write goes through
 * `GetOrCreateInvoiceForMonthUseCase`, which needs the open one to classify the target month.
 * These tests pin that creating a card either leaves both rows behind or reports failure —
 * never a card alone with a success.
 */
class AddCreditCardUseCaseTest {

    private val today = LocalDate(2026, 8, 11)
    private val clock = StoppedClock(today)

    private fun useCase(
        cards: RecordingCardStore,
        invoices: RecordingInvoiceStore,
    ) = AddCreditCardUseCaseImpl(
        repository = cards,
        openInvoiceUseCase = OpenInvoiceUseCaseImpl(
            invoiceRepository = invoices,
            creditCardRepository = cards,
            clock = clock,
        ),
        validateCreditCardName = ValidateCreditCardNameUseCase(cards),
        clock = clock,
    )

    private val form = CreditCardForm(
        name = "Nubank",
        limit = "100000",
        closingDayUser = "5",
        dueDayUser = "15",
    )

    @Test
    fun `creating a card opens its first invoice on the cycle it is in today`() = runTest {
        val cards = RecordingCardStore(newId = 7)
        val invoices = RecordingInvoiceStore()

        val result = useCase(cards, invoices)(form, currency = "BRL")

        assertTrue(result.isRight(), "creating a valid card should succeed")
        assertEquals(1, invoices.inserts.size, "the card should be born with exactly one invoice")

        val opened = invoices.inserts.single()
        assertEquals(Invoice.Status.OPEN, opened.status)
        // Closing day 5 with today on the 11th: the card is already in the cycle that
        // opened in August and closes in September.
        assertEquals(YearMonth(2026, 8), opened.openingMonth)
        assertEquals(YearMonth(2026, 9), opened.closingMonth)
    }

    @Test
    fun `failing to open the first invoice fails the creation`() = runTest {
        val cards = RecordingCardStore(newId = 7)
        // Contrived on purpose: the only way opening refuses a brand-new card is to find a
        // window already taken by it. What is under test is that the refusal reaches the
        // caller, not the odds of reaching it.
        val invoices = RecordingInvoiceStore(
            testInvoice(
                id = 1,
                openingMonth = YearMonth(2026, 8),
                status = Invoice.Status.OPEN,
                card = testCard().copy(id = 7),
            )
        )

        val result = useCase(cards, invoices)(form, currency = "BRL")

        assertTrue(result.isLeft(), "a card whose invoice did not open is not a created card")
        assertTrue(invoices.inserts.isEmpty(), "the refused opening should have written nothing")
    }

    @Test
    fun `a duplicate name is refused before anything is written`() = runTest {
        val cards = RecordingCardStore(
            existing = listOf(testCard().copy(name = "Nubank")),
            newId = 7,
        )
        val invoices = RecordingInvoiceStore()

        val result = useCase(cards, invoices)(form, currency = "BRL")

        assertTrue(result.isLeft())
        assertTrue(cards.inserts.isEmpty(), "the card should not be written")
        assertTrue(invoices.inserts.isEmpty(), "no invoice should be written for a card that does not exist")
    }
}

/** Records what the creation writes, and answers the name check from what already exists. */
private class RecordingCardStore(
    private val existing: List<CreditCard> = emptyList(),
    private val newId: Long = 1,
) : ICreditCardRepository {

    val inserts = mutableListOf<CreditCard>()

    override suspend fun insert(creditCard: CreditCard, currency: String): Long {
        inserts += creditCard
        return newId
    }

    override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard> = existing
    override suspend fun getAllCreditCards(): List<CreditCard> = existing

    override suspend fun getCreditCardById(creditCardId: Long): CreditCard? =
        inserts.firstOrNull()?.copy(id = newId)

    override fun observeAllCreditCards(): Flow<List<CreditCard>> = notUnderTest()
    override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>> = notUnderTest()
    override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> = notUnderTest()
    override suspend fun currencyForNewCard(): String = notUnderTest()
    override suspend fun update(creditCard: CreditCard) = notUnderTest()
    override suspend fun delete(creditCard: CreditCard) = notUnderTest()
    override suspend fun unarchive(accountId: Long) = notUnderTest()
}

private fun notUnderTest(): Nothing = error("not part of the card creation under test")
