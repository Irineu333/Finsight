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
            DimensionPeriodTotals(expense = 10_000, advancePayment = 3_000, adjustment = 1_000),
            entryDao.dimensionPeriodTotals(dimensionId = 1),
        )
    }

    @Test
    fun `card month totals span every card and exclude other months and adjustments`() = runTest {
        seed()

        assertEquals(
            LiabilityMonthTotals(expense = 10_000, payment = 3_000),
            entryDao.liabilityMonthTotals("2026-03"),
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
        assertEquals(-18_900L, entryDao.netWorthCents())
    }
}
