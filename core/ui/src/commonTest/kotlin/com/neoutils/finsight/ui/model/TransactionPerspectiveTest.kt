package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.extension.DisplayAmount
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Fixes the invariant left after the D6 fallback dissolved (D21): a
 * [TransactionPerspective] is an account id — a card enters the same way, through
 * its `accountId` — and it decides which leg of a transaction the screen reads.
 * The same transfer read from its two accounts yields opposite directions and opposite
 * signs; an account with no leg yields no item at all.
 */
class TransactionPerspectiveTest {

    private val source = Account(id = 1L, name = "Source", type = AccountType.ASSET, currency = "BRL")
    private val destination = Account(id = 2L, name = "Destination", type = AccountType.ASSET, currency = "BRL")

    private val transfer = Transaction(
        id = 1L,
        title = "Op",
        date = LocalDate(2026, 1, 1),
        entries = listOf(
            Entry(account = source, amount = -10_000),
            Entry(account = destination, amount = 10_000),
        ),
    )

    private fun uiFrom(accountId: Long) = transfer.toTransactionUi(TransactionPerspective(accountId).accountId)

    @Test
    fun perspectiveSelectsTheLegOfItsAccount() {
        val outgoing = uiFrom(source.id)
        val incoming = uiFrom(destination.id)

        assertEquals(TransactionType.EXPENSE, outgoing?.direction)
        assertEquals(TransactionType.INCOME, incoming?.direction)
        // Under a perspective a transfer is signed at both ends — the two legs share
        // label, icon and color, so the sign is all that tells them apart.
        assertEquals(DisplayAmount.explicitSign(-100.0, "BRL", isApproximate = false), outgoing?.amount)
        assertEquals(DisplayAmount.explicitSign(100.0, "BRL", isApproximate = false), incoming?.amount)
        // The transaction's nature does not depend on who is looking.
        assertEquals(TransactionLabel.TRANSFER, outgoing?.label)
        assertEquals(TransactionLabel.TRANSFER, incoming?.label)
    }

    @Test
    fun eachLegIsDenominatedByItsOwnAccountAndNotByTheBase() {
        // Design D29, and it has to be checked with a currency that differs from the
        // base: with them equal the violation renders exactly the same text.
        val foreign = Account(id = 3L, name = "Chase", type = AccountType.ASSET, currency = "USD")
        val conversionBrl =
            Account(id = 4L, name = "Conversion", type = AccountType.CONVERSION, currency = "BRL")
        val conversionUsd =
            Account(id = 5L, name = "Conversion", type = AccountType.CONVERSION, currency = "USD")

        val crossing = Transaction(
            id = 2L,
            title = "Op",
            date = LocalDate(2026, 1, 1),
            entries = listOf(
                Entry(account = source, amount = -55_000),
                Entry(account = conversionBrl, amount = 55_000),
                Entry(account = conversionUsd, amount = -10_000),
                Entry(account = foreign, amount = 10_000),
            ),
        )

        assertEquals("BRL", crossing.toTransactionUi(source.id)?.amount?.currency)
        assertEquals("USD", crossing.toTransactionUi(foreign.id)?.amount?.currency)
    }

    // --- Which end states the figure of a cross-currency operation ---

    private val card = Account(id = 6L, name = "Card", type = AccountType.LIABILITY, currency = "BRL")

    /** A dollar account paying off a real card: US$ 550,00 left, R$ 500,00 was paid. */
    private fun crossCurrencyPayment() = Transaction(
        id = 4L,
        title = "Op",
        date = LocalDate(2026, 1, 1),
        entries = listOf(
            Entry(
                account = Account(id = 7L, name = "Chase", type = AccountType.ASSET, currency = "USD"),
                amount = -55_000,
            ),
            Entry(account = card, amount = 50_000),
            Entry(
                account = Account(id = 8L, name = "Conv", type = AccountType.CONVERSION, currency = "USD"),
                amount = 55_000,
            ),
            Entry(
                account = Account(id = 9L, name = "Conv", type = AccountType.CONVERSION, currency = "BRL"),
                amount = -50_000,
            ),
        ),
    )

    @Test
    fun theEndAlreadyInTheBaseStatesTheFigure() {
        val ui = crossCurrencyPayment().toTransactionUi(baseCurrency = "BRL")

        assertEquals("BRL", ui?.amount?.currency)
        assertEquals(500.0, ui?.amount?.value)
        // Nothing was converted, so nothing is approximate — it is the ledger's own
        // figure, read off the end the user keeps his accounts in.
        assertEquals(false, ui?.amount?.isApproximate)
        // And the direction stays with the leg the transaction is *read* through, or a
        // card payment would announce itself as income.
        assertEquals(TransactionType.EXPENSE, ui?.direction)
    }

    @Test
    fun withNeitherEndInTheBaseTheReadingIsWhatItWas() {
        // A euro base over a dollar-to-real payment: converting would buy a currency
        // nobody asked for at the price of a rate that may not even exist.
        val ui = crossCurrencyPayment().toTransactionUi(baseCurrency = "EUR")

        assertEquals("USD", ui?.amount?.currency)
        assertEquals(550.0, ui?.amount?.value)
    }

    @Test
    fun aPerspectiveOutranksTheBase() {
        // Opened from the dollar account's statement, the line is that account's, and
        // the base has no say (design D29).
        val ui = crossCurrencyPayment().toTransactionUi(accountId = 7L, baseCurrency = "BRL")

        assertEquals("USD", ui?.amount?.currency)
    }

    @Test
    fun aSingleCurrencyOperationReadsTheSameLegItAlwaysRead() {
        // Both ends in the base: there is nothing to prefer, and the outgoing leg stays
        // the one the figure comes from.
        val ui = transfer.toTransactionUi(baseCurrency = "BRL")

        assertEquals(100.0, ui?.amount?.value)
        assertEquals("BRL", ui?.amount?.currency)
    }

    @Test
    fun aStatementLineIsNeverApproximate() {
        // A line is a single entry: nothing was reconciled to produce it.
        assertEquals(false, uiFrom(source.id)?.amount?.isApproximate)
    }

    @Test
    fun perspectiveWithoutALegYieldsNoItem() {
        assertNull(uiFrom(accountId = 99L))
    }

    // --- The neutral leg names its criterion: the negative monetary leg (D16) ---
    // Not a defect fix — `min` returned the same leg on every balanced transaction,
    // which is why the cases that prove the change are the ones with *no* negative
    // monetary leg and the one with two legs of the same sign, never the crossing.

    private val income = Account(id = 3L, name = "Income", type = AccountType.INCOME, currency = "BRL")

    @Test
    fun aTransactionWithNoNegativeMonetaryLegKeepsTheLegItAlreadyRead() {
        val received = Transaction(
            id = 2L,
            title = "Op",
            date = LocalDate(2026, 1, 1),
            entries = listOf(
                Entry(account = source, amount = 10_000),
                Entry(account = income, amount = -10_000),
            ),
        )

        assertEquals(source.id, received.primaryEntry?.account?.id)
    }

    @Test
    fun theNeutralLegIsChosenBySignAndNotByMagnitude() {
        // Two monetary legs sharing a sign is what `min` was silently wrong about:
        // it would have answered "the largest outflow", a magnitude comparison that
        // means nothing once the two legs can be in different currencies.
        val sameSign = Transaction(
            id = 3L,
            title = "Op",
            date = LocalDate(2026, 1, 1),
            entries = listOf(
                Entry(account = source, amount = -10_000),
                Entry(account = destination, amount = -90_000),
                Entry(account = income, amount = 100_000),
            ),
        )

        assertEquals(source.id, sameSign.primaryEntry?.account?.id)
    }
}
