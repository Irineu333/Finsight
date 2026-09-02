package com.neoutils.finsight

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.usecase.AdvanceInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finsight.domain.usecase.UpdateAdvanceInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.impliedRate
import com.neoutils.finsight.extension.liabilityLeg
import com.neoutils.finsight.extension.sourceLeg
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Correcting a partial invoice payment end to end, through the real graph.
 *
 * What only the real graph can answer is here: the write boundary rebalances the legs it
 * is handed, `InvoiceWriteGuard` gets a say on the invoice the operation leaves *and* on
 * the one it arrives at, and the owed figures come out of SQL rather than out of a fake.
 *
 * The correction **keeps the operation**. That is what separates it from deleting and
 * registering another, and it is the whole reason this change exists.
 */
class EditInvoicePaymentEndToEndTest {

    // Both cycles lie in the past, which is what the "not in the future" guard asks of
    // a payment's date. Card closing day is 10, so an invoice due in March opens on
    // 10 February and closes on 10 March.
    private val marchCycle = YearMonth(2026, 3)
    private val aprilCycle = YearMonth(2026, 4)
    private val inMarchCycle = LocalDate(2026, 2, 20)
    private val inAprilCycle = LocalDate(2026, 3, 20)

    @Test
    fun `a correction moves the amount, the account and the invoice, and stays one operation`() =
        runApp(baseCurrency = "BRL") {
            val nubank = account("Nubank", currency = "BRL", isDefault = true)
            val savings = account("Savings", currency = "BRL")
            income(nubank, amount = 5_000.0, date = inMarchCycle)
            income(savings, amount = 5_000.0, date = inMarchCycle)

            val card = card("Card", currency = "BRL")
            val march = invoice(card, marchCycle)
            val april = invoice(card, aprilCycle)
            cardExpense(card, march, amount = 800.0, date = inMarchCycle)
            cardExpense(card, april, amount = 500.0, date = inAprilCycle)

            val owed = get<CalculateInvoiceUseCase>()

            get<AdvanceInvoicePaymentUseCase>()(
                invoiceId = march.id,
                amount = 300.0,
                date = inMarchCycle,
                account = nubank,
            ).onLeft { error("the payment was refused: $it") }

            assertEquals(500.0, owed(march))

            val payment = transactions.getAllTransactions()
                .single { it.entries.liabilityLeg()?.dimensionId == march.dimensionId && it.entries.sourceLeg() != null }
            val legIdsBefore = entries.getEntriesByTransaction(payment.id).map { it.id }.toSet()

            // Raising it past what the invoice currently owes: R$ 700 against R$ 500,
            // expressible only because the operation stops counting against itself.
            get<UpdateAdvanceInvoicePaymentUseCase>()(
                transactionId = payment.id,
                invoiceId = march.id,
                amount = 700.0,
                date = inMarchCycle,
                account = nubank,
            ).onLeft { error("the correction was refused: $it") }

            assertEquals(100.0, owed(march))
            assertEquals(
                1,
                transactions.getAllTransactions().count { it.entries.sourceLeg()?.amount?.let { a -> a < 0 } == true && it.entries.liabilityLeg() != null },
                "one payment, corrected — not a second one",
            )

            // Now the invoice, the account and the date at once.
            get<UpdateAdvanceInvoicePaymentUseCase>()(
                transactionId = payment.id,
                invoiceId = april.id,
                amount = 300.0,
                date = inAprilCycle,
                account = savings,
            ).onLeft { error("the correction across invoices was refused: $it") }

            assertEquals(800.0, owed(march), "the invoice it left owes it again")
            assertEquals(200.0, owed(april), "the invoice it arrived at discounts it")

            val corrected = assertNotNull(transactions.getTransactionById(payment.id))
            assertEquals(payment.id, corrected.id)
            assertEquals(inAprilCycle, corrected.date)
            assertEquals(savings.id, corrected.entries.sourceLeg()?.account?.id)
            assertEquals(april.dimensionId, corrected.entries.liabilityLeg()?.dimensionId)
            assertTrue(
                entries.getEntriesByTransaction(payment.id).none { it.id in legIdsBefore },
                "the legs are rewritten, which is what the boundary does with a rewrite",
            )
            assertEquals(
                Invoice.Status.OPEN,
                invoices.getInvoiceById(april.id)?.status,
                "no path here marks an invoice PAID",
            )
        }

    @Test
    fun `correcting a payment between currencies rebalances it and teaches the archive`() =
        runApp(baseCurrency = "BRL") {
            val nubank = account("Nubank", currency = "BRL", isDefault = true)
            income(nubank, amount = 5_000.0, date = inMarchCycle)

            val chase = card("Chase", currency = "USD")
            val march = invoice(chase, marchCycle)
            cardExpense(chase, march, amount = 100.0, date = inMarchCycle)

            get<AdvanceInvoicePaymentUseCase>()(
                invoiceId = march.id,
                amount = 50.0,
                date = inMarchCycle,
                account = nubank,
                paidAmount = 275.0,
            ).onLeft { error("the cross-currency payment was refused: $it") }

            val payment = transactions.getAllTransactions()
                .single { it.entries.any { entry -> entry.account.type == AccountType.CONVERSION } }

            get<UpdateAdvanceInvoicePaymentUseCase>()(
                transactionId = payment.id,
                invoiceId = march.id,
                amount = 90.0,
                date = inMarchCycle,
                account = nubank,
                paidAmount = 520.0,
            ).onLeft { error("the correction was refused: $it") }

            val legs = entries.getEntriesByTransaction(payment.id)

            legs.groupBy { it.currency }.forEach { (currency, group) ->
                assertEquals(0L, group.sumOf { it.amount }, "$currency does not sum to zero")
            }

            assertEquals(-52_000L, assertNotNull(legs.sourceLeg()).amount)
            assertEquals(9_000L, assertNotNull(legs.liabilityLeg()).amount)
            assertTrue(
                legs.filter { it.account.type == AccountType.CONVERSION }.all { it.dimensionId == null },
                "the conversion residue does not belong to the invoice",
            )
            assertEquals(10.0, get<CalculateInvoiceUseCase>()(march))

            // The rate the detail reads off the operation's own two ends, and the rate
            // the archive learned from the same crossing: one quotient, not two.
            val out = assertNotNull(legs.sourceLeg())
            val into = assertNotNull(legs.liabilityLeg())
            val shown = assertNotNull(
                impliedRate(
                    sourceAmount = -out.amount / 100.0,
                    targetAmount = into.amount / 100.0,
                )
            )

            val harvested = get<IExchangeRateRepository>().observeAll().first()
                .single { it.source == ExchangeRate.Source.DERIVED }

            assertEquals("BRL", harvested.currency)
            assertEquals("USD", harvested.counterCurrency)
            assertEquals(shown, harvested.rate)
            assertEquals(90.0 / 520.0, shown)
        }
}
