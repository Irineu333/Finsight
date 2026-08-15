package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.usecase.HarvestExchangeRateUseCase
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
import kotlin.test.assertTrue

/**
 * Settling an invoice is two writes that have to agree: a transaction that moves the
 * money, and a status that says it moved. The order matters — the invoice is marked
 * paid only after the transaction lands — and so does the shape of the intent: the
 * card's leg carries the invoice's dimension and the account's does not, because two
 * dimensioned legs would cancel the invoice out instead of settling it.
 *
 * The escaping-exception case is documented in the use case as a bug that already
 * happened once. This is what keeps it from happening twice.
 */
class PayInvoicePaymentUseCaseTest {

    private val card = testCard()
    private val account = Account(id = 42, name = "Wallet", currency = "BRL")
    private val paymentDay = LocalDate(2026, 2, 10)

    private val closed = testInvoice(
        openingMonth = YearMonth(2026, 1),
        status = Invoice.Status.CLOSED,
        card = card,
    )

    private fun useCase(
        store: RecordingInvoiceStore,
        writer: RecordingTransactionWriter,
        owed: Map<Long, Double>,
    ): PayInvoicePaymentUseCase = PayInvoicePaymentUseCaseImpl(
        transactionRepository = writer,
        invoiceRepository = store,
        calculateInvoiceUseCase = CalculateInvoiceUseCase(FakeEntryRepository(owed)),
        payInvoiceUseCase = PayInvoiceUseCase(store, StoppedClock(LocalDate(2026, 2, 20))),
        // Same-currency throughout, so there is no rate to learn and no second
        // denomination to resolve.
        harvestExchangeRate = HarvestExchangeRateUseCase(NoExchangeRates),
        accountRepository = FakeCardAccountRepository(),
    )

    @Test
    fun `the payment moves what is owed, and only the card's leg is dimensioned`() = runTest {
        val store = RecordingInvoiceStore(closed)
        val writer = RecordingTransactionWriter()

        useCase(store, writer, owed = mapOf(100L to 70.0))(closed.id, paymentDay, account)

        val intent = assertNotNull(writer.captured)
        assertEquals(paymentDay, intent.date)

        val fromAccount = intent.legs.single { it.accountId == account.id }
        val toCard = intent.legs.single { it.accountId == card.accountId }

        assertEquals(TransactionType.EXPENSE, fromAccount.type)
        assertEquals(70.0, fromAccount.amount)
        assertNull(
            fromAccount.dimensionId,
            "the money leaves undimensioned: a dimension on both legs would net the invoice to zero " +
                "by cancellation rather than by payment",
        )

        assertEquals(TransactionType.INCOME, toCard.type)
        assertEquals(70.0, toCard.amount)
        assertEquals(closed.dimensionId, toCard.dimensionId)
    }

    @Test
    fun `the invoice is marked paid after the money moved`() = runTest {
        val store = RecordingInvoiceStore(closed)
        val writer = RecordingTransactionWriter()

        val result = useCase(store, writer, owed = mapOf(100L to 70.0))(closed.id, paymentDay, account)

        assertTrue(result.isRight())
        assertEquals(Invoice.Status.PAID, store.byId(closed.id)?.status)
        assertEquals(paymentDay, store.byId(closed.id)?.paidAt)
    }

    @Test
    fun `a write refused by the ledger comes back as a Left, not as a crash`() = runTest {
        // `either {}` intercepts a Raise, never a thrown exception. Before the write was
        // wrapped in `catch {}`, an archived paying account threw straight past the
        // Either and out of the ViewModel.
        val store = RecordingInvoiceStore(closed)
        val writer = RecordingTransactionWriter(failure = IllegalStateException("account is archived"))

        val result = useCase(store, writer, owed = mapOf(100L to 70.0))(closed.id, paymentDay, account)

        assertIs<IllegalStateException>(result.leftOrNull())
        assertEquals(
            Invoice.Status.CLOSED,
            store.byId(closed.id)?.status,
            "and the invoice is not left claiming to be paid for money that never moved",
        )
    }

    @Test
    fun `an invoice that is not closed yet is not paid this way`() = runTest {
        val open = testInvoice(status = Invoice.Status.OPEN, card = card)
        val store = RecordingInvoiceStore(open)
        val writer = RecordingTransactionWriter()

        val result = useCase(store, writer, owed = mapOf(100L to 70.0))(open.id, paymentDay, account)

        val error = assertIs<InvoiceException>(result.leftOrNull())
        assertEquals(InvoiceError.InvoiceNotClosed, error.error)
        assertNull(writer.captured, "nothing is written for a refusal")
    }

    @Test
    fun `an invoice that owes nothing has nothing to settle`() = runTest {
        val store = RecordingInvoiceStore(closed)
        val writer = RecordingTransactionWriter()

        val result = useCase(store, writer, owed = mapOf(100L to 0.0))(closed.id, paymentDay, account)

        val error = assertIs<InvoiceException>(result.leftOrNull())
        assertEquals(InvoiceError.InvoiceNotInDebt, error.error)
        assertNull(writer.captured)
    }

    @Test
    fun `an invoice in credit is not settled by paying more into it`() = runTest {
        val store = RecordingInvoiceStore(closed)
        val writer = RecordingTransactionWriter()

        val result = useCase(store, writer, owed = mapOf(100L to -20.0))(closed.id, paymentDay, account)

        assertEquals(InvoiceError.InvoiceNotInDebt, (result.leftOrNull() as InvoiceException).error)
        assertNull(writer.captured)
    }

    @Test
    fun `an invoice that is not there is not paid`() = runTest {
        val writer = RecordingTransactionWriter()

        val result = useCase(RecordingInvoiceStore(), writer, owed = emptyMap())(99, paymentDay, account)

        assertEquals(InvoiceError.NotFound, (result.leftOrNull() as InvoiceException).error)
        assertNull(writer.captured)
    }
}
