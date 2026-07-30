package com.neoutils.finsight.database

import com.neoutils.finsight.database.dao.LiabilityMonthTotals
import com.neoutils.finsight.database.dao.DimensionPeriodTotals
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.domain.model.DimensionKind
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The card and sub-ledger aggregates: a sub-ledger's own breakdown, the month-wide
 * card totals, and the all-time balance of a card's account.
 *
 * "Sub-ledger" is the ledger's word for what a card screen calls an invoice — the
 * queries know only a dimension, and never join a facade table to find out whose
 * it is.
 */
class InvoiceAndCardQueryTest {

    private val database = ledgerDatabase()
    private val entryDao = database.entryDao()

    @AfterTest fun tearDown() = database.close()

    private suspend fun seed() = LedgerFixture(database).apply {
        account(1, AccountEntity.Type.ASSET, "Bank")
        account(2, AccountEntity.Type.LIABILITY, "Card")
        account(10, AccountEntity.Type.EXPENSE, "Despesas")
        account(30, AccountEntity.Type.EQUITY, "Recon")
        dimension(1, DimensionKind.INVOICE)
        dimension(2, DimensionKind.INVOICE)
        dimension(10, DimensionKind.CATEGORY)

        // Two purchases on the card, both on invoice 1.
        transaction("2026-03-05", (2L posts -6_000).taggedWith(1), (10L posts 6_000).taggedWith(10))
        transaction("2026-03-08", (2L posts -4_000).taggedWith(1), (10L posts 4_000).taggedWith(10))
        // An advance payment: only the card leg carries the sub-ledger.
        transaction("2026-03-10", (2L posts 3_000).taggedWith(1), 1L posts -3_000)
        // An adjustment, told apart by its EQUITY counter-leg.
        transaction("2026-03-12", (2L posts 1_000).taggedWith(1), 30L posts -1_000)
        // Next month, and a different invoice — excluded from March and from invoice 1.
        transaction("2026-04-03", (2L posts -9_900).taggedWith(2), (10L posts 9_900).taggedWith(10))
    }

    @Test
    fun `a sub-ledger's totals classify the legs carrying its dimension`() = runTest {
        seed()

        assertEquals(
            DimensionPeriodTotals(
                currency = "BRL",
                expense = 10_000,
                advancePayment = 3_000,
                adjustment = 1_000,
            ),
            entryDao.dimensionPeriodTotals(dimensionId = 1).sole(),
        )
    }

    @Test
    fun `card month totals span every card, exclude other months, and report adjustment`() = runTest {
        seed()

        // The adjustment has a class of its own — signed, in the ledger's natural sign —
        // instead of falling silently outside both expense and payment.
        assertEquals(
            LiabilityMonthTotals(
                currency = "BRL",
                expense = 10_000,
                payment = 3_000,
                adjustment = 1_000,
            ),
            entryDao.liabilityMonthTotals("2026-03").sole(),
        )
    }

    @Test
    fun `balanceOf is the all-time natural balance of the card account`() = runTest {
        seed()

        // -6000 -4000 +3000 +1000 -9900 = -15900 owed, across both invoices.
        assertEquals(-15_900L, entryDao.balanceOf(accountId = 2))
    }

    @Test
    fun `net worth spans the monetary accounts and ignores the nominal ones`() = runTest {
        seed()

        // Bank(-3000) + Card(-15900); the expense and equity legs are not money.
        assertEquals(-18_900L, entryDao.netWorthCents().sole().total)
    }

    // --- the sub-ledger reads are per currency, by construction and not by kind (task 4.7) ---

    @Test
    fun `owed and flows of a card in a foreign currency come back in that currency`() = runTest {
        LedgerFixture(database).apply {
            account(1, AccountEntity.Type.ASSET, "Bank")
            account(2, AccountEntity.Type.LIABILITY, "Amex", currency = "USD")
            account(11, AccountEntity.Type.EXPENSE, "Expenses", currency = "USD")
            dimension(1, DimensionKind.INVOICE)

            transaction(
                "2026-03-05",
                (2L posts -6_000).taggedWith(1) inCurrency "USD",
                11L posts 6_000 inCurrency "USD",
            )
        }

        // One key, and it is the card's own currency — never the base by omission. That
        // the map holds a single key here is the *card facade's* guarantee, not the
        // ledger's: nothing in these queries ties a dimension to one account, which is
        // why they group all the same.
        assertEquals(
            DimensionPeriodTotals(
                currency = "USD",
                expense = 6_000,
                advancePayment = 0,
                adjustment = 0,
            ),
            entryDao.dimensionPeriodTotals(dimensionId = 1).sole(),
        )
        assertEquals(-6_000L, entryDao.dimensionNaturalBalance(dimensionId = 1).sole().total)
    }

    @Test
    fun `card month totals and net worth keep two currencies apart`() = runTest {
        LedgerFixture(database).apply {
            account(1, AccountEntity.Type.ASSET, "Bank")
            account(2, AccountEntity.Type.LIABILITY, "Card")
            account(3, AccountEntity.Type.LIABILITY, "Amex", currency = "USD")
            account(10, AccountEntity.Type.EXPENSE, "Despesas")
            account(11, AccountEntity.Type.EXPENSE, "Expenses", currency = "USD")

            transaction("2026-03-05", 2L posts -6_000, 10L posts 6_000)
            transaction(
                "2026-03-06",
                3L posts -1_000 inCurrency "USD",
                11L posts 1_000 inCurrency "USD",
            )
            transaction("2026-03-07", 1L posts 20_000, 10L posts -20_000)
        }

        val cards = entryDao.liabilityMonthTotals("2026-03")
        assertEquals(6_000L, cards.forCurrency("BRL")?.expense)
        assertEquals(1_000L, cards.forCurrency("USD")?.expense)

        val worth = entryDao.netWorthCents()
        assertEquals(14_000L, worth.forCurrency("BRL")?.total, "20000 held less 6000 owed")
        assertEquals(-1_000L, worth.forCurrency("USD")?.total)
        assertEquals(2, worth.size, "no rate is applied and nothing is summed across currencies")
    }
}
