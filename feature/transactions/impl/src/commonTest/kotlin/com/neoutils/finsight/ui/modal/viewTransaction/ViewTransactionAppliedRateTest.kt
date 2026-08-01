package com.neoutils.finsight.ui.modal.viewTransaction

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Transaction
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rate the detail shows is derived from the operation's own two ends, and from
 * nothing else — no rate is persisted anywhere on the write path (design D6), and the
 * archive is not consulted: what this row states is what *this* operation applied, which
 * may legitimately differ from the rate on file for that day.
 */
class ViewTransactionAppliedRateTest {

    private val date = LocalDate(2026, 1, 1)

    private fun entry(type: AccountType, amount: Long, currency: String) = Entry(
        account = Account(name = "$type-$currency", type = type, currency = currency),
        amount = amount,
    )

    private fun content(entries: List<Entry>) = ViewTransactionUiState.Content(
        transaction = Transaction(id = 1L, title = "Op", date = date, entries = entries),
    )

    @Test
    fun crossCurrencyTransferStatesTheRateItApplied() {
        // R$ 550,00 left, US$ 100,00 arrived — plus the two conversion legs the write
        // boundary posts, which take no part in the quotient.
        val content = content(
            listOf(
                entry(AccountType.ASSET, -55_000, "BRL"),
                entry(AccountType.ASSET, 10_000, "USD"),
                entry(AccountType.CONVERSION, 55_000, "BRL"),
                entry(AccountType.CONVERSION, -10_000, "USD"),
            )
        )

        val applied = requireNotNull(content.appliedRate)
        assertEquals("BRL", applied.sourceCurrency)
        assertEquals("USD", applied.targetCurrency)
        // One real buys 0,181818… dollars — the full quotient, never a rounded form.
        assertEquals(10_000.0 / 55_000.0, applied.rate)
    }

    @Test
    fun crossCurrencyInvoicePaymentStatesItsRateToo() {
        // The same reading holds wherever two monetary legs disagree on currency: the
        // liability leg is the one the money entered.
        val content = content(
            listOf(
                entry(AccountType.ASSET, -55_000, "BRL"),
                entry(AccountType.LIABILITY, 10_000, "USD"),
                entry(AccountType.CONVERSION, 55_000, "BRL"),
                entry(AccountType.CONVERSION, -10_000, "USD"),
            )
        )

        assertEquals(10_000.0 / 55_000.0, requireNotNull(content.appliedRate).rate)
    }

    @Test
    fun singleCurrencyTransferHasNoRateToState() {
        val content = content(
            listOf(
                entry(AccountType.ASSET, -10_000, "BRL"),
                entry(AccountType.ASSET, 10_000, "BRL"),
            )
        )

        assertNull(content.appliedRate)
    }

    @Test
    fun accountsAreToldApartByCurrencyOnlyWhenTwoAreOnScreen() {
        val crossCurrency = content(
            listOf(
                entry(AccountType.ASSET, -55_000, "BRL"),
                entry(AccountType.LIABILITY, 10_000, "USD"),
                entry(AccountType.CONVERSION, 55_000, "BRL"),
                entry(AccountType.CONVERSION, -10_000, "USD"),
            )
        )
        assertTrue(crossCurrency.namesAccountCurrency)

        // The question is about this operation, never about the app: a dollar-only
        // operation reads the same for someone who also keeps accounts in reais, and
        // its own amount already says which currency it is in.
        val singleCurrency = content(
            listOf(
                entry(AccountType.ASSET, -5_000, "USD"),
                entry(AccountType.EXPENSE, 5_000, "USD"),
            )
        )
        assertFalse(singleCurrency.namesAccountCurrency)
    }

    @Test
    fun cardPurchaseHasNothingToDivideBy() {
        // One monetary leg only: the credited liability. There is no second end, so
        // there is no quotient — and no row.
        val content = content(
            listOf(
                entry(AccountType.LIABILITY, -10_000, "USD"),
                entry(AccountType.EXPENSE, 10_000, "USD"),
            )
        )

        assertNull(content.appliedRate)
    }
}
