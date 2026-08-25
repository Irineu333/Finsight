package com.neoutils.finsight.database

import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.repository.EntryRepository
import com.neoutils.finsight.domain.model.DimensionKind
import com.neoutils.finsight.domain.model.MoneyByCurrency
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The monthly series of a dimension: the window read in one query, cut at the top by
 * whoever asked.
 *
 * What these pin down is that the series is the *same* aggregate as the single-month
 * read, only grouped — so the two can never disagree — and that a month with no entry
 * is absent rather than zero, which is what forces the zeros of a window to be supplied
 * where the window is decided.
 */
class EntryDimensionSeriesQueryTest {

    private val database = ledgerDatabase()
    private val entryDao = database.entryDao()
    private val repository = EntryRepository(entryDao)

    @AfterTest fun tearDown() = database.close()

    private suspend fun seed() = LedgerFixture(database).apply {
        account(1, AccountEntity.Type.ASSET, "Nubank")
        account(2, AccountEntity.Type.ASSET, "Chase", currency = "USD")
        account(10, AccountEntity.Type.EXPENSE, "Despesas")
        account(11, AccountEntity.Type.EXPENSE, "Expenses", currency = "USD")
        dimension(10, DimensionKind.CATEGORY)
    }

    @Test
    fun `two currencies in a month come back as one line each, unsummed`() = runTest {
        seed().apply {
            transaction("2026-01-10", (10L posts 5_000).taggedWith(10), 1L posts -5_000)
            transaction(
                "2026-01-12",
                (11L posts 1_200).taggedWith(10) inCurrency "USD",
                2L posts -1_200 inCurrency "USD",
            )
            transaction("2026-02-03", (10L posts 2_500).taggedWith(10), 1L posts -2_500)
        }

        assertEquals(
            mapOf(
                YearMonth(2026, 1) to MoneyByCurrency.of(mapOf("BRL" to 50.0, "USD" to 12.0)),
                YearMonth(2026, 2) to MoneyByCurrency.of("BRL", 25.0),
            ),
            repository.dimensionMonthlySeriesByCurrency(dimensionId = 10, upTo = YearMonth(2026, 3)),
        )
    }

    @Test
    fun `a month with no entry of the dimension is absent, not zero`() = runTest {
        seed().apply {
            transaction("2026-01-10", (10L posts 5_000).taggedWith(10), 1L posts -5_000)
            // March moves the ledger, but not this dimension: February and March alike
            // have nothing to say about it.
            transaction("2026-03-10", 10L posts 900, 1L posts -900)
        }

        assertEquals(
            listOf(YearMonth(2026, 1)),
            repository.dimensionMonthlySeriesByCurrency(dimensionId = 10, upTo = YearMonth(2026, 6)).keys.toList(),
        )
    }

    @Test
    fun `a month of the series is the same figure the single-month read answers`() = runTest {
        seed().apply {
            transaction("2026-01-10", (10L posts 5_000).taggedWith(10), 1L posts -5_000)
            transaction("2026-01-22", (10L posts 1_750).taggedWith(10), 1L posts -1_750)
            transaction(
                "2026-01-25",
                (11L posts 400).taggedWith(10) inCurrency "USD",
                2L posts -400 inCurrency "USD",
            )
        }

        val series = repository.dimensionMonthlySeriesByCurrency(dimensionId = 10, upTo = YearMonth(2026, 1))

        assertEquals(
            repository.dimensionBalanceInMonthByCurrency(YearMonth(2026, 1), dimensionId = 10),
            series.getValue(YearMonth(2026, 1)),
            "both derive from the same aggregate, so they cannot disagree",
        )
    }

    @Test
    fun `the upper cut leaves out every month after it`() = runTest {
        seed().apply {
            // A purchase in instalments: the ledger holds the future months already.
            transaction("2026-01-05", (10L posts 3_000).taggedWith(10), 1L posts -3_000)
            transaction("2026-02-05", (10L posts 3_000).taggedWith(10), 1L posts -3_000)
            transaction("2026-03-05", (10L posts 3_000).taggedWith(10), 1L posts -3_000)
        }

        assertEquals(
            listOf(YearMonth(2026, 1), YearMonth(2026, 2)),
            repository.dimensionMonthlySeriesByCurrency(dimensionId = 10, upTo = YearMonth(2026, 2)).keys.toList(),
            "the cut is inclusive of its own month and of nothing after it",
        )
    }
}
