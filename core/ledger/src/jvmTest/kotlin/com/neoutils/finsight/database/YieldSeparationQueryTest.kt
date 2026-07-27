package com.neoutils.finsight.database

import com.neoutils.finsight.database.dao.AccountPeriodTotals
import com.neoutils.finsight.database.dao.AssetMonthTotals
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.domain.model.DimensionKind
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The one thing the ledger knows about yield: a dimension identity, handed in from
 * outside. It never learns that the dimension is a category, still less that the
 * category means "yield" — what it does is repartition income by a `Long`.
 *
 * The seed puts a salary and a yield on the **same day, in the same account**, which
 * is the case the whole design turns on: the two are indistinguishable in shape, and
 * only the dimension tells them apart.
 */
class YieldSeparationQueryTest {

    private val database = ledgerDatabase()
    private val entryDao = database.entryDao()

    @AfterTest fun tearDown() = database.close()

    private companion object {
        const val SALARY_DIMENSION = 20L
        const val YIELD_DIMENSION = 30L
    }

    private suspend fun seed() = LedgerFixture(database).apply {
        account(1, AccountEntity.Type.ASSET, "A")
        account(3, AccountEntity.Type.ASSET, "B")
        account(10, AccountEntity.Type.EXPENSE, "Despesas")
        account(20, AccountEntity.Type.INCOME, "Receitas")
        dimension(SALARY_DIMENSION, DimensionKind.CATEGORY)
        dimension(YIELD_DIMENSION, DimensionKind.CATEGORY)

        // Salary and yield, same account, same day.
        transaction("2026-01-05", 1L posts 500_000, (20L posts -500_000).taggedWith(SALARY_DIMENSION))
        transaction("2026-01-05", 1L posts 1_240, (20L posts -1_240).taggedWith(YIELD_DIMENSION))
        // A second yield in the same month adds up; it does not replace the first.
        transaction("2026-01-28", 1L posts 800, (20L posts -800).taggedWith(YIELD_DIMENSION))
        // Yield on the other account of the perimeter, for the month-wide read.
        transaction("2026-01-15", 3L posts 500, (20L posts -500).taggedWith(YIELD_DIMENSION))
        transaction("2026-01-10", 1L posts -3_000, (10L posts 3_000).taggedWith(SALARY_DIMENSION))
    }

    @Test
    fun `with no yield dimension the account totals are the undivided ones`() = runTest {
        seed()

        assertEquals(
            AccountPeriodTotals(
                income = 502_040, // salary + both yields, all in one line
                yield = 0,
                expense = 3_000,
                adjustment = 0,
                settlement = 0,
            ),
            entryDao.accountPeriodTotals(1, "2026-01", yieldDimensionId = null),
        )
    }

    @Test
    fun `the yield leaves income and lands on its own line`() = runTest {
        seed()

        val totals = entryDao.accountPeriodTotals(1, "2026-01", yieldDimensionId = YIELD_DIMENSION)

        assertEquals(500_000, totals.income) // the salary alone
        assertEquals(2_040, totals.yield)    // 12,40 + 8,00, both of them
        assertEquals(3_000, totals.expense)
    }

    @Test
    fun `the partition stays total - income plus yield is what income alone was`() = runTest {
        seed()

        val undivided = entryDao.accountPeriodTotals(1, "2026-01", yieldDimensionId = null)
        val split = entryDao.accountPeriodTotals(1, "2026-01", yieldDimensionId = YIELD_DIMENSION)

        assertEquals(undivided.income, split.income + split.yield)
        assertEquals(undivided.expense, split.expense)
        assertEquals(undivided.adjustment, split.adjustment)
        assertEquals(undivided.settlement, split.settlement)
    }

    @Test
    fun `separating by another dimension leaves the yield inside income`() = runTest {
        seed()

        val totals = entryDao.accountPeriodTotals(1, "2026-01", yieldDimensionId = SALARY_DIMENSION)

        assertEquals(2_040, totals.income) // the two yields
        assertEquals(500_000, totals.yield)
    }

    @Test
    fun `the month-wide read separates yield across every asset account`() = runTest {
        seed()

        assertEquals(
            AssetMonthTotals(income = 500_000, yield = 2_540, expense = 3_000, adjustment = 0),
            entryDao.assetMonthTotals("2026-01", yieldDimensionId = YIELD_DIMENSION),
        )
    }

    @Test
    fun `with no yield dimension the month-wide totals are the undivided ones`() = runTest {
        seed()

        assertEquals(
            AssetMonthTotals(income = 502_540, yield = 0, expense = 3_000, adjustment = 0),
            entryDao.assetMonthTotals("2026-01", yieldDimensionId = null),
        )
    }
}
