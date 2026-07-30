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
            listOf(
                DimensionPeriodTotals(
                    currency = "BRL",
                    expense = 10_000,
                    advancePayment = 3_000,
                    adjustment = 1_000,
                )
            ),
            entryDao.dimensionPeriodTotals(dimensionId = 1),
        )
    }

    @Test
    fun `card month totals span every card, exclude other months, and report adjustment`() = runTest {
        seed()

        // The adjustment has a class of its own — signed, in the ledger's natural sign —
        // instead of falling silently outside both expense and payment.
        assertEquals(
            listOf(LiabilityMonthTotals(currency = "BRL", expense = 10_000, payment = 3_000, adjustment = 1_000)),
            entryDao.liabilityMonthTotals("2026-03"),
        )
    }

    @Test
    fun `balanceOf is the all-time natural balance of the card account`() = runTest {
        seed()

        // -6000 -4000 +3000 +1000 -9900 = -15900 owed, across both invoices.
        assertEquals(-15_900L, entryDao.balanceOf(accountId = 2).cents())
    }

    @Test
    fun `net worth spans the monetary accounts and ignores the nominal ones`() = runTest {
        seed()

        // Bank(-3000) + Card(-15900); the expense and equity legs are not money.
        assertEquals(-18_900L, entryDao.netWorthCents().cents())
    }

    @Test
    fun `net worth keeps a foreign card apart from the home ones`() = runTest {
        seed().apply {
            account(3, AccountEntity.Type.LIABILITY, "Dollar card", currency = "USD")
            dimension(3, DimensionKind.INVOICE)
            transaction(
                "2026-03-20",
                (3L posts -5_000).taggedWith(3).denominatedIn("USD"),
                (10L posts 5_000).taggedWith(10).denominatedIn("USD"),
            )
        }

        val netWorth = entryDao.netWorthCents()

        // Two figures, and the app's net worth is not one of them yet: reconciling them is
        // consolidation, and it does not happen in the ledger.
        assertEquals(2, netWorth.size)
        assertEquals(-18_900L, netWorth.cents())
        assertEquals(-5_000L, netWorth.cents("USD"))
    }

    @Test
    fun `a card invoice paid across currencies still owes in the card's currency only`() = runTest {
        // The payment leaves a real account, the card leg lands in the card's currency, and
        // the exchange residue carries no dimension — which is what keeps the sub-ledger
        // single-currency and lets the card facade reduce its own figure.
        seed().apply {
            account(3, AccountEntity.Type.LIABILITY, "Dollar card", currency = "USD")
            account(40, AccountEntity.Type.CONVERSION, "Câmbio BRL", currency = "BRL")
            account(41, AccountEntity.Type.CONVERSION, "Câmbio USD", currency = "USD")
            dimension(3, DimensionKind.INVOICE)
            transaction(
                "2026-03-21",
                (3L posts -5_000).taggedWith(3).denominatedIn("USD"),
                (10L posts 5_000).taggedWith(10).denominatedIn("USD"),
            )
            // Paying 50 USD with 275 BRL: two currencies, each balanced by its conversion leg.
            transaction(
                "2026-03-22",
                (3L posts 5_000).taggedWith(3).denominatedIn("USD"),
                (41L posts -5_000).denominatedIn("USD"),
                1L posts -27_500,
                40L posts 27_500,
            )
        }

        val owed = entryDao.dimensionNaturalBalance(dimensionId = 3)

        assertEquals(1, owed.size, "the invoice's own dimension never leaves the card's currency")
        assertEquals(0L, owed.cents("USD"))
    }
}
