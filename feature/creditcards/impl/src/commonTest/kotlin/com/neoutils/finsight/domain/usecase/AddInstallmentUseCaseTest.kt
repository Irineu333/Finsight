package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.right
import com.neoutils.finsight.domain.error.InstallmentError
import com.neoutils.finsight.domain.exception.InstallmentException
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.form.TransactionForm
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A purchase split across invoices is arithmetic the E2E suite could only ever check
 * by eye, and only on the figures it happened to pick: `installments/lifecycle` splits
 * $960.00 in three, which divides exactly. The interesting cases are the ones that do
 * not divide, and they are here.
 *
 * The rule under test: **the parts equal the whole.** The write boundary rounds each
 * leg to cents independently, so dividing the amount and letting each share round on
 * its own is how a cent goes missing while the installment row keeps claiming the full
 * total. Splitting happens in cents, and the last instalment carries the remainder.
 */
class AddInstallmentUseCaseTest {

    private val card = CreditCard(
        id = 1,
        name = "Card",
        limit = 10_000.0,
        closingDay = 5,
        dueDay = 15,
        accountId = 10,
    )

    private val firstDueMonth = YearMonth(2026, 3)
    private val purchaseDate = LocalDate(2026, 1, 20)

    private fun invoice(
        dueMonth: YearMonth,
        status: Invoice.Status = Invoice.Status.OPEN,
        id: Long = dueMonth.year * 100L + dueMonth.month.ordinal,
    ) = Invoice(
        id = id,
        creditCard = card,
        dimensionId = id * 1000,
        openingMonth = dueMonth.plus(-2, DateTimeUnit.MONTH),
        closingMonth = dueMonth.plus(-1, DateTimeUnit.MONTH),
        dueMonth = dueMonth,
        status = status,
    )

    private fun form(amount: String) = TransactionForm(
        type = TransactionType.EXPENSE,
        amount = amount,
        title = "Purchase",
        date = "20/01/2026",
        category = null,
        target = TransactionTarget.CREDIT_CARD,
        creditCard = card,
        invoiceDueMonth = firstDueMonth,
        account = null,
    )

    private fun useCase(
        transactions: FakeTransactionWriter = FakeTransactionWriter(),
        installments: FakeInstallmentStore = FakeInstallmentStore(),
        existing: List<Invoice> = emptyList(),
        total: Double = 960.0,
    ) = AddInstallmentUseCaseImpl(
        transactionRepository = transactions,
        installmentRepository = installments,
        invoiceRepository = FakeInvoiceReader(existing),
        buildTransactionUseCase = FakeTransactionBuilder(total, purchaseDate),
        getOrCreateInvoiceForMonthUseCase = FakeInvoiceOpener(existing, card),
    )

    private fun List<TransactionIntent>.cents() = map { (it.legs.single().amount * 100).roundToLong() }

    // --- The parts equal the whole -----------------------------------------------------------

    @Test
    fun `an amount that does not divide loses no cent`() = runTest {
        val writer = FakeTransactionWriter()

        useCase(transactions = writer, total = 1_000.0).invoke(form("1000,00"), installments = 3)

        // Before the split moved into cents this was three legs of $333.33: $999.99
        // recorded against an installment row still claiming $1,000.00.
        assertEquals(listOf(33_333L, 33_333L, 33_334L), writer.captured.cents())
        assertEquals(100_000L, writer.captured.cents().sum())
    }

    @Test
    fun `the remainder rides on the last instalment, however many cents it is`() = runTest {
        val writer = FakeTransactionWriter()

        useCase(transactions = writer, total = 999.99).invoke(form("999,99"), installments = 7)

        val cents = writer.captured.cents()
        assertEquals(99_999L, cents.sum())
        assertEquals(List(6) { 14_285L } + 14_289L, cents)
    }

    @Test
    fun `an amount that divides exactly is untouched by any of this`() = runTest {
        val writer = FakeTransactionWriter()

        useCase(transactions = writer, total = 960.0).invoke(form("960,00"), installments = 3)

        assertEquals(listOf(32_000L, 32_000L, 32_000L), writer.captured.cents())
    }

    @Test
    fun `the smallest amounts still add up`() = runTest {
        // Ten cents in three: two instalments of nothing and one of ten, which is
        // ugly and correct. Losing a cent to make it pretty would be neither.
        val writer = FakeTransactionWriter()

        useCase(transactions = writer, total = 0.10).invoke(form("0,10"), installments = 3)

        assertEquals(listOf(3L, 3L, 4L), writer.captured.cents())
        assertEquals(10L, writer.captured.cents().sum())
    }

    @Test
    fun `the installment row records the whole purchase, not a share of it`() = runTest {
        val store = FakeInstallmentStore()

        useCase(installments = store, total = 1_000.0).invoke(form("1000,00"), installments = 3)

        assertEquals(3, store.created?.count)
        assertEquals(1_000.0, store.created?.totalAmount)
    }

    // --- One instalment per invoice, one invoice per month ------------------------------------

    @Test
    fun `each instalment is a month later, and numbered in order`() = runTest {
        val writer = FakeTransactionWriter()

        useCase(transactions = writer, total = 960.0).invoke(form("960,00"), installments = 3)

        assertEquals(listOf(1, 2, 3), writer.captured.map { it.installmentNumber })
        assertEquals(
            listOf(purchaseDate, LocalDate(2026, 2, 20), LocalDate(2026, 3, 20)),
            writer.captured.map { it.date },
        )
    }

    @Test
    fun `each share is tagged with the invoice it lands on`() = runTest {
        val existing = listOf(
            invoice(firstDueMonth),
            invoice(firstDueMonth.plus(1, DateTimeUnit.MONTH)),
            invoice(firstDueMonth.plus(2, DateTimeUnit.MONTH)),
        )
        val writer = FakeTransactionWriter()

        useCase(transactions = writer, existing = existing).invoke(form("960,00"), installments = 3)

        assertEquals(
            existing.map { it.dimensionId },
            writer.captured.map { it.legs.single().dimensionId },
        )
    }

    // --- What it refuses ----------------------------------------------------------------------

    @Test
    fun `one instalment is not an installment`() = runTest {
        val result = useCase().invoke(form("960,00"), installments = 1)

        val error = assertIs<InstallmentException>(result.leftOrNull())
        assertEquals(InstallmentError.MinInstallment, error.error)
    }

    @Test
    fun `a closed invoice in the middle blocks the purchase, and says which instalment`() = runTest {
        // The second of three months is already closed. E2E cannot reach this: its flow
        // creates the card precisely so that all three invoices are open.
        val blocked = invoice(firstDueMonth.plus(1, DateTimeUnit.MONTH), Invoice.Status.CLOSED)
        val writer = FakeTransactionWriter()

        val result = useCase(
            transactions = writer,
            existing = listOf(invoice(firstDueMonth), blocked),
        ).invoke(form("960,00"), installments = 3)

        val error = assertIs<InstallmentException>(result.leftOrNull())
        val reason = assertIs<InstallmentError.BlockedInvoice>(error.error)
        assertEquals(2, reason.installment)
        assertEquals(blocked.id, reason.invoice.id)
        assertTrue(writer.captured.isEmpty(), "nothing is written when one instalment cannot land")
    }

    @Test
    fun `a write that fails takes the installment row with it`() = runTest {
        // One decision by the user, one unit of work: an installment row left behind
        // would describe money no transaction holds.
        val store = FakeInstallmentStore()

        val result = useCase(
            transactions = FakeTransactionWriter(failing = true),
            installments = store,
        ).invoke(form("960,00"), installments = 3)

        assertTrue(result.isLeft())
        assertNull(store.created, "the installment row was created and then removed")
    }
}

// --- Fakes -------------------------------------------------------------------------------------

private class FakeTransactionBuilder(
    private val amount: Double,
    private val date: LocalDate,
) : BuildTransactionUseCase {
    override suspend fun invoke(form: TransactionForm): Either<Throwable, TransactionIntent> =
        TransactionIntent(
            title = form.title,
            date = date,
            legs = listOf(TransactionLeg(TransactionType.EXPENSE, amount, accountId = 99)),
        ).right()
}

/** Hands back what it was seeded with, and mints anything else on demand. */
private class FakeInvoiceOpener(
    private val existing: List<Invoice>,
    private val creditCard: CreditCard,
) : GetOrCreateInvoiceForMonthUseCase {
    override suspend fun invoke(
        creditCardId: Long,
        targetDueMonth: YearMonth,
    ): Either<Throwable, Invoice> {
        val found = existing.find { it.dueMonth == targetDueMonth }
        return (found ?: Invoice(
            id = targetDueMonth.year * 100L + targetDueMonth.month.ordinal,
            creditCard = creditCard,
            dimensionId = (targetDueMonth.year * 100L + targetDueMonth.month.ordinal) * 1000,
            openingMonth = targetDueMonth.plus(-2, DateTimeUnit.MONTH),
            closingMonth = targetDueMonth.plus(-1, DateTimeUnit.MONTH),
            dueMonth = targetDueMonth,
            status = Invoice.Status.OPEN,
        )).right()
    }
}

private class FakeTransactionWriter(
    private val failing: Boolean = false,
) : ITransactionRepository {

    var captured: List<TransactionIntent> = emptyList()
        private set

    override suspend fun createTransactions(intents: List<TransactionIntent>): List<Transaction> {
        if (failing) error("write refused")
        captured = intents
        return emptyList()
    }

    override fun observeAllTransactions(): Flow<List<Transaction>> = outOfScope()
    override fun observeTransactionsBy(
        date: LocalDate?,
        dimensionId: Long?,
        accountId: Long?,
    ): Flow<List<Transaction>> = outOfScope()

    override fun observeTransactionById(id: Long): Flow<Transaction?> = outOfScope()
    override suspend fun getAllTransactions(): List<Transaction> = outOfScope()
    override suspend fun getTransactionById(id: Long): Transaction? = outOfScope()
    override suspend fun getExistingTransactionIds(ids: Collection<Long>): Set<Long> = outOfScope()
    override suspend fun createTransaction(intent: TransactionIntent): Transaction = outOfScope()
    override suspend fun updateTransaction(
        id: Long,
        title: String?,
        date: LocalDate,
        leg: TransactionLeg,
        contra: ContraLeg?,
    ) = outOfScope()
    override suspend fun deleteTransactionById(id: Long) = outOfScope()
    override suspend fun deleteTransactionsByIds(ids: List<Long>) = outOfScope()
}

private class FakeInstallmentStore : IInstallmentRepository {

    var created: Installment? = null
        private set

    override suspend fun createInstallment(count: Int, totalAmount: Double): Long {
        created = Installment(id = 7, count = count, totalAmount = totalAmount)
        return 7
    }

    override suspend fun deleteInstallmentById(id: Long) {
        if (created?.id == id) created = null
    }

    override fun observeAllInstallments(): Flow<List<Installment>> = outOfScope()
    override suspend fun getAllInstallments(): List<Installment> = outOfScope()
    override suspend fun getInstallmentById(id: Long): Installment? = outOfScope()
    override suspend fun updateInstallment(id: Long, count: Int, totalAmount: Double) = outOfScope()
}

private class FakeInvoiceReader(private val invoices: List<Invoice>) : IInvoiceRepository {

    override suspend fun getInvoicesByCreditCard(creditCardId: Long) = invoices

    override fun observeAllInvoices(): Flow<List<Invoice>> = outOfScope()
    override fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>> = outOfScope()
    override fun observeInvoiceById(invoiceId: Long): Flow<Invoice?> = outOfScope()
    override fun observeOpenInvoice(creditCardId: Long): Flow<Invoice?> = outOfScope()
    override fun observeAvailableInvoices(creditCardId: Long): Flow<List<Invoice>> = outOfScope()
    override fun observeUnpaidInvoice(creditCardId: Long): Flow<Invoice?> = outOfScope()
    override fun observeUnpaidInvoices(): Flow<List<Invoice>> = outOfScope()
    override suspend fun getAllInvoices(): List<Invoice> = outOfScope()
    override suspend fun getUnpaidInvoicesByCreditCard(creditCardId: Long): List<Invoice> = outOfScope()
    override suspend fun getUnpaidInvoicesByCreditCards(creditCardIds: Collection<Long>): Map<Long, List<Invoice>> =
        creditCardIds.associateWith { getUnpaidInvoicesByCreditCard(it) }.filterValues { it.isNotEmpty() }
    override suspend fun getOpenInvoice(creditCardId: Long): Invoice? = outOfScope()
    override suspend fun getInvoiceById(id: Long): Invoice? = outOfScope()
    override suspend fun insert(invoice: Invoice): Invoice = outOfScope()
    override suspend fun update(invoice: Invoice) = outOfScope()
    override suspend fun deleteById(id: Long) = outOfScope()
}

private fun outOfScope(): Nothing = error("not part of what AddInstallmentUseCase does")
