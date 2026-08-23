package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.testing.FakeCardAccountRepository
import com.neoutils.finsight.testing.NoExchangeRates
import com.neoutils.finsight.ui.screen.invoiceTransactions.FakeEntryRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Paying part of an invoice is refused by *what the invoice accepts* before it is
 * refused by when the payment is dated — the guard reads the same predicate the screens
 * read, so an invoice the interface does not offer is one the domain does not permit.
 *
 * The retroactive case is the one the offer predicate opens: a past cycle is regularized
 * with every date inside it, which is the whole of its window.
 */
class AdvanceInvoicePaymentUseCaseTest {

    private val card = testCard()
    private val account = Account(id = 42, name = "Wallet", currency = "BRL")

    /** Late enough that even the retroactive cycle below is entirely behind it. */
    private val today = LocalDate(2026, 6, 1)

    private fun useCase(
        store: RecordingInvoiceStore,
        writer: RecordingTransactionWriter,
        owed: Map<Long, Double>,
    ) = AdvanceInvoicePaymentUseCase(
        writeInvoicePayment = WriteInvoicePaymentUseCase(
            transactionRepository = writer,
            // Same-currency throughout, so there is no rate to learn.
            harvestExchangeRate = HarvestExchangeRateUseCase(NoExchangeRates),
            accountRepository = FakeCardAccountRepository(),
        ),
        validateInvoicePayment = ValidateInvoicePaymentUseCase(
            invoiceRepository = store,
            calculateInvoiceUseCase = CalculateInvoiceUseCase(FakeEntryRepository(owed)),
            clock = StoppedClock(today),
        ),
    )

    @Test
    fun `an open invoice takes part of what it owes, and only the card's leg is dimensioned`() = runTest {
        // Window [2026-05-05, 2026-06-05], which today falls inside.
        val open = testInvoice(
            openingMonth = YearMonth(2026, 5),
            status = Invoice.Status.OPEN,
            card = card,
        )
        val store = RecordingInvoiceStore(open)
        val writer = RecordingTransactionWriter()

        val result = useCase(store, writer, owed = mapOf(100L to 800.0))(
            invoiceId = open.id,
            amount = 300.0,
            date = LocalDate(2026, 5, 20),
            account = account,
        )

        assertNull(result.leftOrNull())

        val intent = assertNotNull(writer.captured)
        val fromAccount = intent.legs.single { it.accountId == account.id }
        val toCard = intent.legs.single { it.accountId == card.accountId }

        assertEquals(TransactionType.EXPENSE, fromAccount.type)
        assertEquals(300.0, fromAccount.amount)
        assertNull(fromAccount.dimensionId)

        assertEquals(TransactionType.INCOME, toCard.type)
        assertEquals(300.0, toCard.amount)
        assertEquals(open.dimensionId, toCard.dimensionId)

        assertEquals(
            Invoice.Status.OPEN,
            store.byId(open.id)?.status,
            "a part is not a discharge: the invoice goes on receiving spending",
        )
    }

    @Test
    fun `a retroactive invoice takes part of what it owes, dated inside its own past cycle`() = runTest {
        // Window [2026-01-05, 2026-02-05] — wholly behind today.
        val retroactive = testInvoice(
            openingMonth = YearMonth(2026, 1),
            status = Invoice.Status.RETROACTIVE,
            card = card,
        )
        val store = RecordingInvoiceStore(retroactive)
        val writer = RecordingTransactionWriter()

        val result = useCase(store, writer, owed = mapOf(100L to 800.0))(
            invoiceId = retroactive.id,
            amount = 300.0,
            date = LocalDate(2026, 1, 20),
            account = account,
        )

        assertNull(result.leftOrNull())

        val intent = assertNotNull(writer.captured)
        assertEquals(LocalDate(2026, 1, 20), intent.date)
        assertEquals(300.0, intent.legs.single { it.accountId == card.accountId }.amount)

        assertEquals(
            Invoice.Status.RETROACTIVE,
            store.byId(retroactive.id)?.status,
            "paying it does not settle it: PAID stays reachable only through closing",
        )
    }

    @Test
    fun `a closed invoice takes no part of what it owes`() = runTest {
        val closed = testInvoice(
            openingMonth = YearMonth(2026, 1),
            status = Invoice.Status.CLOSED,
            card = card,
        )
        val store = RecordingInvoiceStore(closed)
        val writer = RecordingTransactionWriter()

        // Dated inside the cycle, so the only thing left to refuse it is the status.
        val result = useCase(store, writer, owed = mapOf(100L to 800.0))(
            invoiceId = closed.id,
            amount = 300.0,
            date = LocalDate(2026, 1, 20),
            account = account,
        )

        val error = assertIs<InvoiceException>(result.leftOrNull())
        assertEquals(InvoiceError.InvoiceNotPartiallyPayable, error.error)
        assertNull(writer.captured, "nothing is written for a refusal")
    }

    @Test
    fun `the refusal names the status and not the date`() = runTest {
        val paid = testInvoice(
            openingMonth = YearMonth(2026, 1),
            status = Invoice.Status.PAID,
            card = card,
        )
        val store = RecordingInvoiceStore(paid)
        val writer = RecordingTransactionWriter()

        // A date outside the window *and* a status that refuses: the status is read
        // first, so the message says what is actually wrong.
        val result = useCase(store, writer, owed = mapOf(100L to 800.0))(
            invoiceId = paid.id,
            amount = 300.0,
            date = LocalDate(2026, 5, 20),
            account = account,
        )

        assertEquals(
            InvoiceError.InvoiceNotPartiallyPayable,
            (result.leftOrNull() as InvoiceException).error,
        )
    }

    @Test
    fun `the ceiling holds over what the invoice owes`() = runTest {
        val open = testInvoice(
            openingMonth = YearMonth(2026, 5),
            status = Invoice.Status.OPEN,
            card = card,
        )
        val store = RecordingInvoiceStore(open)
        val writer = RecordingTransactionWriter()

        val result = useCase(store, writer, owed = mapOf(100L to 200.0))(
            invoiceId = open.id,
            amount = 300.0,
            date = LocalDate(2026, 5, 20),
            account = account,
        )

        assertEquals(
            InvoiceError.AmountExceedsInvoice,
            (result.leftOrNull() as InvoiceException).error,
        )
        assertNull(writer.captured)
    }
}
