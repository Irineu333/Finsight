package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.ui.screen.invoiceTransactions.FakeEntryRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What an invoice owes, and what it owes once an operation of its own is left out.
 *
 * One formula answers both, and the second is what a correction is judged by: an
 * operation being rewritten already reduced the figure it is about to state again, so a
 * ceiling that counted it would refuse the correction that raises it.
 */
class CalculateInvoiceUseCaseTest {

    private val invoice = testInvoice()

    private val cardAccount = Account(
        id = 10,
        name = "Card",
        type = AccountType.LIABILITY,
        currency = "BRL",
    )

    /** The card's leg of a R$ 300 payment: debit-positive, in cents, dimensioned. */
    private val paymentLeg = Entry(
        id = 1,
        transactionId = 7,
        account = cardAccount,
        amount = 30_000,
        dimensionId = invoice.dimensionId,
    )

    private fun useCase(
        owed: Double,
        entries: Map<Long, List<Entry>> = emptyMap(),
    ) = CalculateInvoiceUseCase(
        FakeEntryRepository(
            owedByInvoiceId = mapOf(checkNotNull(invoice.dimensionId) to owed),
            entriesByTransactionId = entries,
        )
    )

    @Test
    fun `what is owed counts every leg, the payment being corrected included`() = runTest {
        // R$ 800 spent, R$ 300 already paid: the ledger says R$ 500.
        val calculate = useCase(owed = 500.0, entries = mapOf(7L to listOf(paymentLeg)))

        assertEquals(500.0, calculate(invoice))
    }

    @Test
    fun `leaving the operation out gives back what it settled`() = runTest {
        val calculate = useCase(owed = 500.0, entries = mapOf(7L to listOf(paymentLeg)))

        assertEquals(
            800.0,
            calculate(invoice, excluding = 7),
            "the R$ 300 that operation paid come back to the ceiling it is judged by",
        )
    }

    @Test
    fun `an operation with nothing on this invoice changes nothing`() = runTest {
        // The correction that switched invoices: the operation has legs, none of them
        // here, so there is nothing to leave out.
        val elsewhere = paymentLeg.copy(id = 2, transactionId = 9, dimensionId = 999)
        val calculate = useCase(owed = 120.0, entries = mapOf(9L to listOf(elsewhere)))

        assertEquals(120.0, calculate(invoice, excluding = 9))
    }
}
