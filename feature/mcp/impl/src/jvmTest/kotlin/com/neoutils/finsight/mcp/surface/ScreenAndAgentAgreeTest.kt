package com.neoutils.finsight.mcp.surface

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.extension.deriveTransactionType
import com.neoutils.finsight.ui.model.TransactionPerspective
import com.neoutils.finsight.ui.model.legUnder
import com.neoutils.finsight.ui.model.toTransactionUi
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * **The same transaction, presented to a screen and to an agent, agrees with itself.**
 *
 * The two models are deliberately different — one carries an icon and a theme colour, the other
 * carries names an agent would otherwise spend a call to learn — and that is exactly what makes the
 * risk invisible: nothing forces the two mappers to reach the same conclusion, and nobody ever sees
 * their outputs side by side. A second derivation of "which leg do I read" or "which end states the
 * figure" can be wrong for months while both surfaces look perfectly reasonable on their own.
 *
 * Three things are held equal here, and they are the three that have owners:
 *
 * - **the label**, which comes from the account types of the legs (`deriveTransactionLabel`);
 * - **the leg that is read**, which the perspective decides (`legUnder`);
 * - **the end that denominates the figure**, which is a different question and is answered by
 *   `figureLegUnder` — an operation crossing currencies has two equally true figures, and picking
 *   the wrong one announces a card payment in a currency the user keeps no accounts in.
 *
 * The cases below are chosen so that a mapper deriving any of them for itself lands on a different
 * answer: a transfer read from each of its ends, and a payment whose two ends disagree on currency
 * under two different bases.
 */
class ScreenAndAgentAgreeTest {

    private val source = Account(id = 1L, name = "Nubank", type = AccountType.ASSET, currency = "BRL")
    private val destination = Account(id = 2L, name = "Itaú", type = AccountType.ASSET, currency = "BRL")
    private val groceries = Account(id = 3L, name = "Mercado", type = AccountType.EXPENSE, currency = "BRL")

    private val expense = Transaction(
        id = 10L,
        title = "Feira",
        date = LocalDate(2026, 3, 14),
        entries = listOf(
            Entry(account = source, amount = -12_345),
            Entry(account = groceries, amount = 12_345),
        ),
    )

    private val transfer = Transaction(
        id = 11L,
        title = "Op",
        date = LocalDate(2026, 3, 15),
        entries = listOf(
            Entry(account = source, amount = -10_000),
            Entry(account = destination, amount = 10_000),
        ),
    )

    /** A dollar account paying off a real card: US$ 550,00 left, R$ 500,00 of the card was paid. */
    private val crossCurrencyPayment = Transaction(
        id = 12L,
        title = "Op",
        date = LocalDate(2026, 3, 16),
        entries = listOf(
            Entry(
                account = Account(id = 7L, name = "Chase", type = AccountType.ASSET, currency = "USD"),
                amount = -55_000,
            ),
            Entry(
                account = Account(id = 6L, name = "Card", type = AccountType.LIABILITY, currency = "BRL"),
                amount = 50_000,
            ),
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
    fun `an expense reads the same on both surfaces`() {
        assertAgree(expense)
    }

    @Test
    fun `a transfer read from each of its ends reads the same on both surfaces`() {
        assertAgree(transfer, TransactionPerspective(source.id))
        assertAgree(transfer, TransactionPerspective(destination.id))
    }

    @Test
    fun `a transfer with nobody looking reads the same on both surfaces`() {
        assertAgree(transfer)
    }

    @Test
    fun `the end that denominates a cross-currency figure is the same on both surfaces`() {
        // The end already in the base states the figure: R$ 500,00 and not US$ 550,00. A mapper
        // that read the leg it takes the *direction* from would answer in dollars here, and only
        // here — every single-currency operation in the app would go on agreeing.
        assertEquals("BRL", assertAgree(crossCurrencyPayment, baseCurrency = "BRL").currency)

        // With neither end in the base the reading stays what it was, because the base is never a
        // fallback: converting would buy a currency nobody asked for.
        assertEquals("USD", assertAgree(crossCurrencyPayment, baseCurrency = "EUR").currency)

        // And a named account outranks the base: the line is that account's, in its currency.
        assertEquals(
            "USD",
            assertAgree(crossCurrencyPayment, TransactionPerspective(7L), baseCurrency = "BRL").currency,
        )
    }

    @Test
    fun `a perspective with no leg yields nothing on either surface`() {
        val stranger = TransactionPerspective(accountId = 99L)

        assertNull(transfer.toTransactionUi(stranger.accountId))
        assertNull(transfer.toAgentTransaction(stranger))
    }

    /**
     * Presents [transaction] both ways and holds the two answers against each other, returning the
     * agent's figure so a case can go on to say which currency it expected.
     */
    private fun assertAgree(
        transaction: Transaction,
        perspective: TransactionPerspective? = null,
        baseCurrency: String? = null,
    ): AgentFigure {
        val ui = transaction.toTransactionUi(perspective?.accountId, baseCurrency = baseCurrency)
        val agent = transaction.toAgentTransaction(perspective, baseCurrency = baseCurrency)

        assertNotNull(ui)
        assertNotNull(agent)

        // --- The label: one derivation, from the account types of the legs ---
        assertEquals(
            ui.label,
            TransactionLabel.valueOf(agent.nature.uppercase()),
            "the two surfaces disagree about what this transaction is",
        )

        // --- The leg that is read: the one the shared definition names ---
        val read = assertNotNull(transaction.legUnder(perspective?.accountId))
        assertEquals(
            read.account.id,
            agent.accountId,
            "the agent read a leg other than the one the perspective names",
        )
        // The screen does not carry the account of the leg it read, but it carries the direction
        // that leg gives — and the other leg of a balanced transaction gives the opposite one.
        assertEquals(
            deriveTransactionType(read.amount, transaction.entries),
            ui.direction,
            "the screen read a leg other than the one the perspective names",
        )

        // --- The figure: same end, same currency, same signed value ---
        assertEquals(
            ui.amount.currency,
            agent.amount.currency,
            "the two surfaces denominate this figure in different currencies",
        )
        assertEquals(
            ui.amount.value,
            agent.amount.amount,
            "the two surfaces state different amounts for the same leg",
        )

        // --- The direction: stated only where there is a point of view to state it from ---
        if (perspective == null) {
            assertNull(agent.direction, "a listing with no perspective has no direction to report")
        } else {
            assertEquals(
                ui.direction,
                TransactionType.valueOf(assertNotNull(agent.direction).uppercase()),
                "the two surfaces disagree about which way the money went",
            )
        }

        return agent.amount
    }
}
