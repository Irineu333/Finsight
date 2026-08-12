package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.model.Invoice
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The one operation that brings an invoice into existence for a month.
 *
 * What is asserted here is the classification — derived from the open invoice and never
 * chosen — and the fact that the window comes from the card. Both have a single owner, so
 * a status that specialised the operation would have to disagree with one of them.
 */
class CreateInvoiceUseCaseTest {

    @Test
    fun `month due before the open invoice is born retroactive`() = runTest {
        // OPEN due 2026-02.
        val store = RecordingInvoiceStore(testInvoice())
        val createInvoice = CreateInvoiceUseCase(store)

        val invoice = createInvoice(testCard(), YearMonth(2026, 1)).getOrNull()

        assertEquals(Invoice.Status.RETROACTIVE, invoice?.status)
    }

    @Test
    fun `month due after the open invoice is born future`() = runTest {
        val store = RecordingInvoiceStore(testInvoice())
        val createInvoice = CreateInvoiceUseCase(store)

        val invoice = createInvoice(testCard(), YearMonth(2026, 3)).getOrNull()

        assertEquals(Invoice.Status.FUTURE, invoice?.status)
    }

    @Test
    fun `the open invoice is the reference, not today`() = runTest {
        // Open invoice due in July while the calendar is well past it: August still falls
        // due after July, so it is future — no clock takes part in the decision.
        val store = RecordingInvoiceStore(
            testInvoice(openingMonth = YearMonth(2026, 6))
        )
        val createInvoice = CreateInvoiceUseCase(store)

        val invoice = createInvoice(testCard(), YearMonth(2026, 8)).getOrNull()

        assertEquals(YearMonth(2026, 7), store.byId(1)?.dueMonth)
        assertEquals(Invoice.Status.FUTURE, invoice?.status)
    }

    @Test
    fun `a month that already has an invoice is refused`() = runTest {
        val store = RecordingInvoiceStore(testInvoice())
        val createInvoice = CreateInvoiceUseCase(store)

        val failure = createInvoice(testCard(), YearMonth(2026, 2)).leftOrNull()

        assertIs<InvoiceException>(failure)
        assertEquals(InvoiceError.AlreadyExists, failure.error)
        assertTrue(store.inserts.isEmpty())
    }

    @Test
    fun `without an open invoice there is no way to classify`() = runTest {
        val store = RecordingInvoiceStore(testInvoice(status = Invoice.Status.PAID))
        val createInvoice = CreateInvoiceUseCase(store)

        val failure = createInvoice(testCard(), YearMonth(2026, 5)).leftOrNull()

        assertIs<InvoiceException>(failure)
        assertEquals(InvoiceError.NoOpenInvoice, failure.error)
        assertTrue(store.inserts.isEmpty())
    }

    @Test
    fun `the window written is the card's own, and never opens`() = runTest {
        val store = RecordingInvoiceStore(testInvoice())
        val createInvoice = CreateInvoiceUseCase(store)

        val invoice = createInvoice(testCard(), YearMonth(2026, 4)).getOrNull()

        // closingDay 5, dueDay 15: the bill falls due in the month the cycle closes.
        assertEquals(YearMonth(2026, 3), invoice?.openingMonth)
        assertEquals(YearMonth(2026, 4), invoice?.closingMonth)
        assertEquals(YearMonth(2026, 4), invoice?.dueMonth)
        assertTrue(invoice?.status != Invoice.Status.OPEN)
        // Born empty: the row is the only thing written, and it carries no ledger identity
        // of its own until the repository creates one.
        assertEquals(1, store.inserts.size)
    }

    @Test
    fun `a postponed due day closes the cycle in the month before`() = runTest {
        // dueDay 5 < closingDay 20: the bill for a cycle only arrives the month after it
        // closes, so an invoice due in April closes in March.
        val card = testCard(closingDay = 20, dueDay = 5)
        val store = RecordingInvoiceStore(
            testInvoice(card = card, openingMonth = YearMonth(2026, 1))
        )
        val createInvoice = CreateInvoiceUseCase(store)

        val invoice = createInvoice(card, YearMonth(2026, 4)).getOrNull()

        assertEquals(YearMonth(2026, 2), invoice?.openingMonth)
        assertEquals(YearMonth(2026, 3), invoice?.closingMonth)
        assertEquals(YearMonth(2026, 4), invoice?.dueMonth)
    }

    /**
     * The gesture and the transaction reach the same code, so they cannot disagree. The
     * assertion is on the produced invoice rather than on the call, because what matters
     * is that no second derivation exists to drift.
     */
    @Test
    fun `creating by gesture and by transaction produce the same invoice`() = runTest {
        val card = testCard()

        val byGestureStore = RecordingInvoiceStore(testInvoice())
        val byGesture = CreateInvoiceUseCase(byGestureStore)(card, YearMonth(2026, 5)).getOrNull()

        val byTransactionStore = RecordingInvoiceStore(testInvoice())
        val byTransaction = GetOrCreateInvoiceForMonthUseCaseImpl(
            invoiceRepository = byTransactionStore,
            createInvoiceUseCase = CreateInvoiceUseCase(byTransactionStore),
        )(card, YearMonth(2026, 5)).getOrNull()

        assertEquals(byGesture?.openingMonth, byTransaction?.openingMonth)
        assertEquals(byGesture?.closingMonth, byTransaction?.closingMonth)
        assertEquals(byGesture?.dueMonth, byTransaction?.dueMonth)
        assertEquals(byGesture?.status, byTransaction?.status)
    }

    @Test
    fun `an existing open month is returned to the transaction, not created again`() = runTest {
        val store = RecordingInvoiceStore(testInvoice())
        val getOrCreate = GetOrCreateInvoiceForMonthUseCaseImpl(
            invoiceRepository = store,
            createInvoiceUseCase = CreateInvoiceUseCase(store),
        )

        val invoice = getOrCreate(testCard(), YearMonth(2026, 2)).getOrNull()

        assertEquals(1, invoice?.id)
        assertTrue(store.inserts.isEmpty())
    }
}
