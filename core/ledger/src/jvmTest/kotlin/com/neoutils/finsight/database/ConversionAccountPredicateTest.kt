package com.neoutils.finsight.database

import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.domain.model.DimensionKind
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The predicates that name account types as **SQL string literals** — `a.type = 'EQUITY'`,
 * `IN ('ASSET','LIABILITY')`, `IN ('EXPENSE','INCOME')` — now that the chart has a sixth
 * type.
 *
 * The compiler cannot reach any of them: adding `CONVERSION` to the enum closed three
 * `when` expressions and left every one of these strings silently unchanged. That is the
 * real risk of opening the set, and this is what covers it. Each aggregate below is asked
 * one question: does an exchange leg change your answer? It must not — a conversion account
 * is neither the user's money nor a category, and the exchange outcome already shows up in
 * the user's own balances once they are expressed in one currency, so counting it too would
 * count it twice.
 */
class ConversionAccountPredicateTest {

    private val database = ledgerDatabase()
    private val entryDao = database.entryDao()
    private val accountDao = database.accountDao()

    @AfterTest fun tearDown() = database.close()

    /**
     * A transfer of 550 reais into 100 dollars: four legs, balanced per currency by the two
     * conversion accounts, which is exactly the shape `LedgerEntryWriter` writes.
     */
    private suspend fun seedCrossCurrencyTransfer() = LedgerFixture(database).apply {
        account(1, AccountEntity.Type.ASSET, "Bank")
        account(2, AccountEntity.Type.ASSET, "Dollars", currency = "USD")
        account(10, AccountEntity.Type.EXPENSE, "Despesas")
        account(30, AccountEntity.Type.EQUITY, "Recon")
        account(40, AccountEntity.Type.CONVERSION, "Câmbio BRL")
        account(41, AccountEntity.Type.CONVERSION, "Câmbio USD", currency = "USD")
        dimension(10, DimensionKind.CATEGORY)

        transaction("2026-05-01", 1L posts 100_000, 30L posts -100_000)
        transaction(
            "2026-05-10",
            1L posts -55_000,
            40L posts 55_000,
            (2L posts 10_000).denominatedIn("USD"),
            (41L posts -10_000).denominatedIn("USD"),
        )
    }

    @Test
    fun `net worth counts the user's accounts and leaves the conversion legs out`() = runTest {
        seedCrossCurrencyTransfer()

        val netWorth = entryDao.netWorthCents()

        // 450 held in reais and 100 in dollars — the conversion legs (+55000 BRL, −10000 USD)
        // would double the exchange if `IN ('ASSET','LIABILITY')` had let them in.
        assertEquals(45_000L, netWorth.cents())
        assertEquals(10_000L, netWorth.cents("USD"))
    }

    @Test
    fun `the accumulated balance by nature never reads a conversion account`() = runTest {
        seedCrossCurrencyTransfer()

        assertEquals(45_000L, entryDao.balanceUpToMonthByType("ASSET", "2026-05").cents())
        assertEquals(10_000L, entryDao.balanceUpToMonthByType("ASSET", "2026-05").cents("USD"))
        assertEquals(emptyList(), entryDao.balanceUpToMonthByType("LIABILITY", "2026-05"))
    }

    @Test
    fun `a conversion leg does not make a transaction an adjustment`() = runTest {
        seedCrossCurrencyTransfer()

        // `eq` is `EXISTS(... a.type = 'EQUITY')`, and a conversion leg is not one: the
        // transfer's own leg must stay in expense, not slide into the adjustment column. Had
        // the predicate matched conversion, this expense would be 0 and the adjustment
        // 155_000 instead of the opening one alone.
        val flows = entryDao.accountPeriodTotals(accountId = 1, yearMonth = "2026-05")

        assertEquals(55_000L, flows?.expense)
        assertEquals(100_000L, flows?.adjustment, "the opening reconciliation, and only it")
    }

    @Test
    fun `the month-wide asset totals ignore the exchange and its residue`() = runTest {
        seedCrossCurrencyTransfer()

        // The cross-currency transfer has no nominal and no EQUITY counter-leg, so it is
        // neither income nor expense here — as a same-currency transfer already was. Only
        // the opening adjustment survives.
        val totals = entryDao.assetMonthTotals("2026-05")

        assertEquals(100_000L, totals.inCurrency()?.adjustment)
        assertEquals(0L, totals.inCurrency()?.expense)
        assertEquals(null, totals.inCurrency("USD"), "no dollar row: the exchange is not a flow")
    }

    @Test
    fun `card month totals see no conversion account`() = runTest {
        seedCrossCurrencyTransfer()

        assertEquals(emptyList(), entryDao.liabilityMonthTotals("2026-05"))
    }

    @Test
    fun `a conversion account is not a nominal, so it carries no category total`() = runTest {
        seedCrossCurrencyTransfer()

        // `a.type = :nominalType` is a parameter, but the pair it may take is fixed by the
        // caller: a conversion leg is not spending, and asking for either nominal finds none.
        val may = LocalDate(2026, 5, 1) to LocalDate(2026, 5, 31)
        assertEquals(
            emptyList(),
            entryDao.totalsByDimensionWithSiblingLeg("EXPENSE", may.first, may.second, listOf(1, 2)),
        )
        assertEquals(
            emptyList(),
            entryDao.totalsByDimensionWithSiblingLeg("INCOME", may.first, may.second, listOf(1, 2)),
        )
    }

    @Test
    fun `a conversion account is never offered as an account of the user's`() = runTest {
        seedCrossCurrencyTransfer()

        assertEquals(
            listOf("Bank", "Dollars"),
            accountDao.getAllAccounts().map { it.name },
            "the two conversion rows share the table with the nominals and must not leak either",
        )
        assertEquals(6, accountDao.getAllLedgerAccounts().size, "and the chart itself holds all of them")
    }
}
