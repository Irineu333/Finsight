package com.neoutils.finsight.database

import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.repository.EntryRepository
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.DimensionKind
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The predicates the compiler cannot reach: the six `EXISTS(... a.type = 'EQUITY')`
 * of `EntryDao` and the `type IN ('ASSET','LIABILITY')` of net worth.
 *
 * Opening the closed set of account types costs almost nothing in the exhaustive
 * `when`s — the compiler finds those. The real risk of the sixth type is here, in SQL
 * string literals nothing type-checks: if `CONVERSION` had been folded into `EQUITY`,
 * every one of these aggregates would have started reading a cross-currency operation
 * as an adjustment, and net worth would have counted the exchange result twice.
 *
 * Each assertion below is therefore about a *non*-effect: the sixth type went in and
 * no predicate changed meaning.
 */
class ConversionAccountPredicateTest {

    private val database = ledgerDatabase()
    private val entryDao = database.entryDao()
    private val repository = EntryRepository(entryDao)

    @AfterTest fun tearDown() = database.close()

    private val march = YearMonth(2026, 3)

    /**
     * The chart of a user with one account per currency, a dollar card, and the two
     * conversion accounts the write boundary creates on demand — one per currency.
     */
    private suspend fun chart() = LedgerFixture(database).apply {
        account(1, AccountEntity.Type.ASSET, "Nubank")
        account(2, AccountEntity.Type.ASSET, "Chase", currency = "USD")
        account(3, AccountEntity.Type.LIABILITY, "Amex", currency = "USD")
        account(10, AccountEntity.Type.EXPENSE, "Despesas")
        account(30, AccountEntity.Type.EQUITY, "Reconciliação")
        account(90, AccountEntity.Type.CONVERSION, "CONVERSION")
        account(91, AccountEntity.Type.CONVERSION, "CONVERSION", currency = "USD")
        dimension(1, DimensionKind.INVOICE)
        dimension(10, DimensionKind.CATEGORY)
    }

    /** R$ 550,00 leaves the Brazilian account and US$ 100,00 lands in the American one. */
    private suspend fun LedgerFixture.crossCurrencyTransfer(date: String = "2026-03-10") =
        transaction(
            date,
            1L posts -55_000,
            90L posts 55_000,
            91L posts -10_000 inCurrency "USD",
            2L posts 10_000 inCurrency "USD",
        )

    // --- the `eq` predicate keeps meaning only "the user reconciled something" ---

    @Test
    fun `a cross-currency transfer is not an adjustment on the source account`() = runTest {
        chart().crossCurrencyTransfer()

        val totals = entryDao.accountPeriodTotals(1, "2026-03", yieldDimensionId = null)

        assertEquals(0L, totals.adjustment, "the conversion leg is not EQUITY, so `eq` stays 0")
        assertEquals(55_000L, totals.expense, "it reads as money leaving, exactly like a transfer")
    }

    @Test
    fun `a cross-currency transfer stays out of the month's income and expense`() = runTest {
        chart().crossCurrencyTransfer()

        // `assetMonthTotals` counts a transaction only when it has a nominal or EQUITY
        // counter-leg — "not a transfer and not a card payment". A cross-currency
        // transfer has neither, so it is excluded on both sides of the currency split.
        assertEquals(emptyList(), entryDao.assetMonthTotals("2026-03", yieldDimensionId = null))
    }

    @Test
    fun `a real adjustment still reads as one, in its own currency`() = runTest {
        chart().transaction("2026-03-11", 2L posts 700 inCurrency "USD", 30L posts -700)

        // The EQUITY leg is what makes it an adjustment; nothing about the sixth type
        // weakened that. (The reconciliation row is the BRL one — a pre-existing
        // system account — and the `eq` predicate does not read its currency.)
        assertEquals(700L, entryDao.accountPeriodTotals(2, "2026-03", yieldDimensionId = null).adjustment)
        assertEquals(700L, entryDao.assetMonthTotals("2026-03", yieldDimensionId = null).sole().adjustment)
    }

    @Test
    fun `a cross-currency invoice payment is a payment, not an adjustment`() = runTest {
        chart().transaction(
            "2026-03-12",
            1L posts -55_000,
            90L posts 55_000,
            91L posts -10_000 inCurrency "USD",
            (3L posts 10_000).taggedWith(1) inCurrency "USD",
        )

        val cards = entryDao.liabilityMonthTotals("2026-03").sole()
        assertEquals("USD", cards.currency)
        assertEquals(10_000L, cards.payment)
        assertEquals(0L, cards.adjustment)

        val invoice = entryDao.dimensionPeriodTotals(dimensionId = 1).sole()
        assertEquals(10_000L, invoice.advancePayment)
        assertEquals(0L, invoice.adjustment)

        val batched = entryDao.periodTotalsByDimension(listOf(1)).single()
        assertEquals(10_000L, batched.advancePayment)
        assertEquals(0L, batched.adjustment)

        // And on the paying account it is a settlement, since the transaction does have
        // a LIABILITY leg — the `li` predicate is untouched too.
        assertEquals(55_000L, entryDao.accountPeriodTotals(1, "2026-03", yieldDimensionId = null).settlement)
    }

    @Test
    fun `the report figures do not read a cross-currency operation as an adjustment`() = runTest {
        chart().crossCurrencyTransfer()

        val rows = entryDao.scopeStats(
            listOf(1),
            LocalDate.parse("2026-03-01"),
            LocalDate.parse("2026-03-31"),
        )

        // Seen from the Brazilian account alone the operation is not internal (its other
        // asset leg is outside the scope), so it is an expense — and never an adjustment.
        assertEquals(55_000L, rows.sole().expense)
        assertEquals(-55_000L, rows.sole().balance)
    }

    // --- net worth: the conversion accounts stay out, and nothing is summed ---

    @Test
    fun `net worth in one currency is the sum of the monetary accounts`() = runTest {
        chart().transaction("2026-03-01", 1L posts 20_000, 10L posts -20_000)

        // Read at the DAO, which is where this figure lives: no repository member
        // carries it, because none has a production caller (task 4.11).
        assertEquals(20_000L, entryDao.netWorthCents().sole().total)
    }

    @Test
    fun `net worth in two currencies returns both, with no rate applied`() = runTest {
        chart().apply {
            transaction("2026-03-01", 1L posts 100_000, 10L posts -100_000)
            crossCurrencyTransfer()
        }

        val worth = entryDao.netWorthCents()

        // R$ 1000 held less the R$ 550 that left; US$ 100 arrived. The two are reported
        // side by side: reducing them to one number is conversion, and conversion is
        // not the ledger's business.
        assertEquals(45_000L, worth.forCurrency("BRL")?.total)
        assertEquals(10_000L, worth.forCurrency("USD")?.total)
        assertEquals(2, worth.size)
    }

    @Test
    fun `the conversion accounts do not participate in net worth`() = runTest {
        chart().crossCurrencyTransfer()

        // The conversion legs are +55000 BRL and -10000 USD. If `IN ('ASSET','LIABILITY')`
        // had grown a third member, BRL would read 0 and USD 0 — the exchange result
        // counted twice, and net worth frozen against the rate it was booked at.
        val worth = entryDao.netWorthCents()

        assertEquals(-55_000L, worth.forCurrency("BRL")?.total)
        assertEquals(10_000L, worth.forCurrency("USD")?.total)
    }

    @Test
    fun `the accumulated balance by nature ignores the conversion accounts too`() = runTest {
        chart().crossCurrencyTransfer()

        // `balanceUpToMonthByType` takes the nature as a parameter, so CONVERSION is
        // reachable only by asking for it — and nothing in the app does.
        val assets = repository.naturalBalanceUpToByCurrency(march, AccountType.ASSET)

        assertEquals(-550.0, assets["BRL"])
        assertEquals(100.0, assets["USD"])
    }
}
