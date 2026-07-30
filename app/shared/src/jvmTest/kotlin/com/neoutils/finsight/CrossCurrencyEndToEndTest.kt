package com.neoutils.finsight

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.domain.usecase.PayInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.TransferBetweenAccountsUseCase
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.deriveTransactionLabel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The change end to end, through the real graph: a dollar account created beside a real
 * one, a transfer that crosses currencies, and a real-denominated account paying the
 * invoice of a dollar card.
 *
 * What it pins is the sentence the whole design rests on — **the invariant takes no
 * exception**. A cross-currency transaction does not unbalance the ledger; it arrives
 * *incomplete* at the write boundary and is completed there, per currency, through the
 * conversion accounts (design D1). Everything else follows from that: the labels stay
 * `TRANSFER` and `PAYMENT` because conversion has a type of its own (D2), the residue
 * carries no dimension so an invoice payment persists at all (D15), the rate is derived
 * from the two ends rather than given (D6, D11), and the figures that did not cross
 * anything stay exact.
 */
class CrossCurrencyEndToEndTest {

    private val march = YearMonth(2026, 3)
    private val day = LocalDate(2026, 3, 15)

    @Test
    fun `a cross-currency transfer balances per currency, keeps its label and harvests its rate`() =
        runApp(baseCurrency = "BRL") {
            val nubank = account("Nubank", currency = "BRL", isDefault = true)
            val chase = account("Chase", currency = "USD")
            income(nubank, amount = 1_000.0, date = day)

            get<TransferBetweenAccountsUseCase>()(
                sourceAccountId = nubank.id,
                destinationAccountId = chase.id,
                amount = 550.0,
                date = day,
                destinationAmount = 100.0,
            ).onLeft { error("the cross-currency transfer was refused: $it") }

            val transfer = transactions.getAllTransactions()
                .single { it.entries.any { entry -> entry.account.type == AccountType.CONVERSION } }
            val legs = entries.getEntriesByTransaction(transfer.id)

            // Four legs, and Σ = 0 **in each currency** — the invariant, with no exception.
            assertEquals(4, legs.size, "a cross-currency transfer is four legs: two of the user, two of conversion")
            legs.groupBy { it.currency }.forEach { (currency, group) ->
                assertEquals(0L, group.sumOf { it.amount }, "the $currency side does not sum to zero")
            }
            assertEquals(setOf("BRL", "USD"), legs.map { it.currency }.toSet())

            // The conversion legs carry no dimension: copying the invoice's would make
            // the boundary refuse the whole transaction (design D15), and a residue
            // belongs to the exchange, not to a sub-ledger.
            legs.filter { it.account.type == AccountType.CONVERSION }.forEach {
                assertNull(it.dimensionId, "a conversion leg carried a dimension")
            }

            // It still reads as a transfer: conversion is not EQUITY, so nothing derives
            // "adjustment" out of it.
            assertEquals(TransactionLabel.TRANSFER, legs.deriveTransactionLabel())

            // The rate was harvested from the two ends, on the day of the operation, and
            // nothing on the way in ever named it.
            val rate = requireNotNull(get<IExchangeRateRepository>().rateAsOf("USD", day)) {
                "the operation did not leave the rate it applied"
            }
            assertEquals(5.5, rate.rate)
            assertEquals(ExchangeRate.Source.DERIVED, rate.source)

            // Each account still reads its own money, exactly.
            val balances = get<CalculateBalanceUseCase>()
            assertEquals(450.0, balances.forAccount(nubank.id, march))
            assertEquals(100.0, balances.forAccount(chase.id, march))

            // And the figure that *did* cross is marked: two currencies, one rate, one
            // approximate total.
            val total = get<ConsolidateMoneyUseCase>()(
                balances(march),
                on = day,
                policy = DisplayAmount::natural,
            )
            assertTrue(total.isApproximate, "a total holding two currencies came out exact")
            assertEquals(1_000.0, total.terms.single().value, "450 BRL + 100 USD at 5.50 is 1000 BRL")
            assertEquals(day, total.asOf)
        }

    @Test
    fun `paying a dollar card from a real account balances per currency and leaves the invoice owed intact`() =
        runApp(baseCurrency = "BRL") {
            val nubank = account("Nubank", currency = "BRL", isDefault = true)
            val amex = card("Amex", currency = "USD")
            val openInvoice = invoice(amex, march)
            income(nubank, amount = 1_000.0, date = day)
            cardExpense(amex, openInvoice, amount = 100.0, date = day)
            val closed = closeInvoice(openInvoice)

            get<PayInvoicePaymentUseCase>()(
                invoiceId = closed.id,
                date = day,
                account = nubank,
                paidAmount = 550.0,
            ).onLeft { error("the cross-currency invoice payment was refused: $it") }

            val payment = transactions.getAllTransactions()
                .single { it.entries.any { entry -> entry.account.type == AccountType.CONVERSION } }
            val legs = entries.getEntriesByTransaction(payment.id)

            legs.groupBy { it.currency }.forEach { (currency, group) ->
                assertEquals(0L, group.sumOf { it.amount }, "the $currency side of the payment does not sum to zero")
            }
            legs.filter { it.account.type == AccountType.CONVERSION }.forEach {
                assertNull(it.dimensionId, "the residue was charged to the invoice's sub-ledger")
            }

            // A payment, not an adjustment — the four behaviours a shared EQUITY would
            // have broken (design D2).
            assertEquals(TransactionLabel.PAYMENT, legs.deriveTransactionLabel())

            // The invoice owed is the card's own money and nothing moved it but the
            // payment: 100 spent, 100 paid.
            val owed = entries.dimensionOwedByCurrency(requireNotNull(closed.dimensionId))
            assertEquals(listOf("USD"), owed.currencies.toList())
            assertEquals(0.0, requireNotNull(owed.singleOrNull()).value)

            // The paying account is exactly 550 lighter, in its own currency.
            assertEquals(450.0, get<CalculateBalanceUseCase>().forAccount(nubank.id, march))
        }
}
