package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.extension.liabilityLeg
import com.neoutils.finsight.extension.sourceLeg
import com.neoutils.finsight.testing.FakeCardAccountRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Correcting a partial invoice payment in place.
 *
 * The operation keeps its identity — the legs are rewritten, not recreated — and every
 * field it is made of is reachable, the invoice included. The ceiling it is judged by
 * leaves the operation's own contribution out, which is what makes raising a payment
 * beyond the current owed a legitimate correction rather than a refusal.
 */
class UpdateAdvanceInvoicePaymentUseCaseTest {

    private val card = testCard()
    private val cardAccount = Account(
        id = card.accountId,
        name = "Card",
        type = AccountType.LIABILITY,
        currency = "BRL",
    )
    private val wallet = Account(id = 42, name = "Wallet", currency = "BRL")
    private val savings = Account(id = 43, name = "Savings", currency = "BRL")

    /** Late enough that every cycle below is behind it. */
    private val today = LocalDate(2026, 6, 1)

    /** Window [2026-05-05, 2026-06-05], which today falls inside. */
    private val invoice = testInvoice(
        openingMonth = YearMonth(2026, 5),
        status = Invoice.Status.OPEN,
        card = card,
    )

    private val dimensionId = checkNotNull(invoice.dimensionId)
    private val payday = LocalDate(2026, 5, 20)

    private fun useCase(
        ledger: InvoicePaymentLedger,
        store: RecordingInvoiceStore,
        rates: RecordingExchangeRates = RecordingExchangeRates(),
        accounts: FakeCardAccountRepository = FakeCardAccountRepository(),
    ): UpdateAdvanceInvoicePaymentUseCase {
        val transactions = LedgerTransactionRepository(ledger)
        return UpdateAdvanceInvoicePaymentUseCase(
            writeInvoicePayment = WriteInvoicePaymentUseCase(
                transactionRepository = transactions,
                harvestExchangeRate = HarvestExchangeRateUseCase(rates),
                accountRepository = accounts,
            ),
            validateInvoicePayment = ValidateInvoicePaymentUseCase(
                invoiceRepository = store,
                calculateInvoiceUseCase = CalculateInvoiceUseCase(LedgerEntryRepository(ledger)),
                clock = StoppedClock(today),
            ),
            transactionRepository = transactions,
        )
    }

    /** R$ 800 spent on the invoice, R$ 300 of it already paid from [wallet]. */
    private fun ledgerWithPayment(): Pair<InvoicePaymentLedger, Long> {
        val ledger = InvoicePaymentLedger(cardAccount, wallet, savings)
        ledger.seedSpending(LocalDate(2026, 5, 10), cardAccount.id, dimensionId, amount = 800.0)
        val paymentId = ledger.create(
            title = null,
            date = payday,
            legs = paymentLegs(cardAccount.id, wallet.id, dimensionId, settling = 300.0),
        )
        return ledger to paymentId
    }

    @Test
    fun `correcting the amount moves what the invoice owes, and the operation stays the same`() = runTest {
        val (ledger, paymentId) = ledgerWithPayment()
        val store = RecordingInvoiceStore(invoice)
        ledger.titleByTransaction[paymentId] = "Adiantamento"

        assertEquals(500.0, ledger.owed(dimensionId))

        val result = useCase(ledger, store)(
            transactionId = paymentId,
            invoiceId = invoice.id,
            amount = 400.0,
            date = payday,
            account = wallet,
        )

        assertNull(result.leftOrNull())
        assertEquals(400.0, ledger.owed(dimensionId))
        assertEquals(
            2,
            ledger.entriesByTransaction.size,
            "the legs were rewritten on the operation itself, not on a second one",
        )
        assertTrue(
            ledger.entriesOf(paymentId).all { it.transactionId == paymentId },
            "the operation keeps the identity it had",
        )
        assertEquals(
            "Adiantamento",
            ledger.titleByTransaction[paymentId],
            "the form does not show a title, so it does not erase one",
        )
        assertEquals(
            Invoice.Status.OPEN,
            store.byId(invoice.id)?.status,
            "correcting a part is not a discharge: no path here marks an invoice PAID",
        )
    }

    @Test
    fun `correcting the paying account moves the money to the other one`() = runTest {
        val (ledger, paymentId) = ledgerWithPayment()
        val store = RecordingInvoiceStore(invoice)

        val result = useCase(ledger, store)(
            transactionId = paymentId,
            invoiceId = invoice.id,
            amount = 300.0,
            date = payday,
            account = savings,
        )

        assertNull(result.leftOrNull())
        assertEquals(savings.id, ledger.entriesOf(paymentId).sourceLeg()?.account?.id)
    }

    @Test
    fun `pointing the correction at another invoice moves the money between the two`() = runTest {
        // A past cycle, wholly behind today, alongside the open one.
        val earlier = testInvoice(
            id = 2,
            openingMonth = YearMonth(2026, 3),
            status = Invoice.Status.RETROACTIVE,
            card = card,
        )
        val earlierDimension = checkNotNull(earlier.dimensionId)

        val ledger = InvoicePaymentLedger(cardAccount, wallet, savings)
        ledger.seedSpending(LocalDate(2026, 5, 10), cardAccount.id, dimensionId, amount = 800.0)
        ledger.seedSpending(LocalDate(2026, 3, 10), cardAccount.id, earlierDimension, amount = 500.0)
        val paymentId = ledger.create(
            title = null,
            date = payday,
            legs = paymentLegs(cardAccount.id, wallet.id, dimensionId, settling = 300.0),
        )

        val store = RecordingInvoiceStore(invoice, earlier)

        val result = useCase(ledger, store)(
            transactionId = paymentId,
            invoiceId = earlier.id,
            amount = 300.0,
            // Inside the earlier invoice's own window, [2026-03-05, 2026-04-05].
            date = LocalDate(2026, 3, 20),
            account = wallet,
        )

        assertNull(result.leftOrNull())
        assertEquals(800.0, ledger.owed(dimensionId), "the invoice it left owes it again")
        assertEquals(200.0, ledger.owed(earlierDimension), "the invoice it arrived at discounts it")
    }

    @Test
    fun `the ceiling leaves the operation's own contribution out`() = runTest {
        val (ledger, paymentId) = ledgerWithPayment()
        val store = RecordingInvoiceStore(invoice)

        // R$ 500 is what the invoice owes with this payment counted; R$ 700 is only
        // expressible because the payment being corrected stops counting against itself.
        val result = useCase(ledger, store)(
            transactionId = paymentId,
            invoiceId = invoice.id,
            amount = 700.0,
            date = payday,
            account = wallet,
        )

        assertNull(result.leftOrNull())
        assertEquals(100.0, ledger.owed(dimensionId))
    }

    @Test
    fun `an invoice that takes no partial payment refuses the correction`() = runTest {
        val (ledger, paymentId) = ledgerWithPayment()
        val closed = testInvoice(
            openingMonth = YearMonth(2026, 5),
            status = Invoice.Status.CLOSED,
            card = card,
        )
        val store = RecordingInvoiceStore(closed)
        val before = ledger.entriesOf(paymentId)

        val result = useCase(ledger, store)(
            transactionId = paymentId,
            invoiceId = closed.id,
            amount = 300.0,
            date = payday,
            account = wallet,
        )

        assertEquals(
            InvoiceError.InvoiceNotPartiallyPayable,
            (result.leftOrNull() as InvoiceException).error,
        )
        assertEquals(before, ledger.entriesOf(paymentId), "nothing is written for a refusal")
    }

    @Test
    fun `correcting a payment between currencies rewrites both ends and teaches the archive`() = runTest {
        val foreignCard = CreditCard(
            id = 2,
            name = "Chase",
            limit = 1_000.0,
            closingDay = 5,
            dueDay = 15,
            accountId = 11,
        )
        val foreignCardAccount = Account(
            id = foreignCard.accountId,
            name = "Chase",
            type = AccountType.LIABILITY,
            currency = "USD",
        )
        val foreignInvoice = testInvoice(
            id = 3,
            openingMonth = YearMonth(2026, 5),
            status = Invoice.Status.OPEN,
            card = foreignCard,
        )
        val foreignDimension = checkNotNull(foreignInvoice.dimensionId)

        val ledger = InvoicePaymentLedger(foreignCardAccount, wallet)
        ledger.seedSpending(LocalDate(2026, 5, 10), foreignCardAccount.id, foreignDimension, amount = 100.0)
        val paymentId = ledger.create(
            title = null,
            date = payday,
            legs = paymentLegs(
                cardAccountId = foreignCardAccount.id,
                payingAccountId = wallet.id,
                dimensionId = foreignDimension,
                settling = 100.0,
                leaving = 550.0,
            ),
        )

        val rates = RecordingExchangeRates()
        val result = useCase(
            ledger = ledger,
            store = RecordingInvoiceStore(foreignInvoice),
            rates = rates,
            accounts = FakeCardAccountRepository(
                accountsById = mapOf(foreignCardAccount.id to foreignCardAccount),
            ),
        )(
            transactionId = paymentId,
            invoiceId = foreignInvoice.id,
            amount = 90.0,
            date = payday,
            account = wallet,
            paidAmount = 520.0,
        )

        assertNull(result.leftOrNull())

        val entries = ledger.entriesOf(paymentId)

        assertEquals(-52_000L, assertNotNull(entries.sourceLeg()).amount)

        val cardLeg = assertNotNull(entries.liabilityLeg())
        assertEquals(9_000L, cardLeg.amount)
        assertEquals(foreignDimension, cardLeg.dimensionId)

        entries.groupBy { it.currency }.forEach { (currency, group) ->
            assertEquals(0L, group.sumOf { it.amount }, "$currency does not sum to zero")
        }

        assertTrue(
            entries.filter { it.account.type == AccountType.CONVERSION }.all { it.dimensionId == null },
            "the conversion residue does not belong to the invoice",
        )

        assertEquals(10.0, ledger.owed(foreignDimension))

        val harvested = assertNotNull(rates.saved.singleOrNull())
        assertEquals("BRL", harvested.currency)
        assertEquals("USD", harvested.counterCurrency)
        assertEquals(90.0 / 520.0, harvested.rate)
        assertEquals(payday, harvested.date)
    }
}
