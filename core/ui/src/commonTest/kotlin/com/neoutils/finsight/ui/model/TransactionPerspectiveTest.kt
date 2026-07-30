package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.extension.Denomination
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

    private val source = Account(currency = "BRL", id = 1L, name = "Source", type = AccountType.ASSET)
    private val destination = Account(currency = "BRL", id = 2L, name = "Destination", type = AccountType.ASSET)

    private val transfer = Transaction(
        id = 1L,
        title = "Op",
        date = LocalDate(2026, 1, 1),
        entries = listOf(
            Entry(currency = "BRL", account = source, amount = -10_000),
            Entry(currency = "BRL", account = destination, amount = 10_000),
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
        assertEquals(DisplayAmount.explicitSign(-100.0, Denomination.exact("BRL")), outgoing?.amount)
        assertEquals(DisplayAmount.explicitSign(100.0, Denomination.exact("BRL")), incoming?.amount)
        // The transaction's nature does not depend on who is looking.
        assertEquals(TransactionLabel.TRANSFER, outgoing?.label)
        assertEquals(TransactionLabel.TRANSFER, incoming?.label)
    }

    @Test
    fun perspectiveWithoutALegYieldsNoItem() {
        assertNull(uiFrom(accountId = 99L))
    }

    /**
     * Each end of a cross-currency transfer reads in **its own** account's currency.
     *
     * This is the half a single-currency suite cannot see: with both accounts in the base,
     * denominating by the leg and denominating by a preference produce the same text, and a
     * site wired to the wrong one passes every test until the day a dollar account exists.
     * The conversion legs are outside both perspectives and take no part in either reading.
     */
    @Test
    fun eachEndOfACrossCurrencyTransferReadsInItsOwnCurrency() {
        val dollars = Account(currency = "USD", id = 3L, name = "Chase", type = AccountType.ASSET)
        val conversionBrl = Account(currency = "BRL", id = 4L, name = "conversion", type = AccountType.CONVERSION)
        val conversionUsd = Account(currency = "USD", id = 5L, name = "conversion", type = AccountType.CONVERSION)
        val crossTransfer = Transaction(
            id = 2L,
            title = "Op",
            date = LocalDate(2026, 1, 1),
            entries = listOf(
                Entry(currency = "BRL", account = source, amount = -55_000),
                Entry(currency = "BRL", account = conversionBrl, amount = 55_000),
                Entry(currency = "USD", account = dollars, amount = 10_000),
                Entry(currency = "USD", account = conversionUsd, amount = -10_000),
            ),
        )

        val outgoing = crossTransfer.toTransactionUi(TransactionPerspective(source.id).accountId)
        val incoming = crossTransfer.toTransactionUi(TransactionPerspective(dollars.id).accountId)

        assertEquals(DisplayAmount.explicitSign(-550.0, Denomination.exact("BRL")), outgoing?.amount)
        assertEquals(DisplayAmount.explicitSign(100.0, Denomination.exact("USD")), incoming?.amount)
        // The conversion legs did not make it an adjustment, and did not make it two
        // different operations either.
        assertEquals(TransactionLabel.TRANSFER, outgoing?.label)
        assertEquals(TransactionLabel.TRANSFER, incoming?.label)
    }
}
