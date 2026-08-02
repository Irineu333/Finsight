package com.neoutils.finsight.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.entity.ExchangeRateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The archive's reading policy, over a real database: **the last observation on or
 * before the date, per pair, the user's winning over a derived one of the same date.**
 *
 * *Per pair* is the part worth a suite of its own. While every row shared one
 * counterpart the partition was invisible; the moment a pair off the base's axis exists,
 * a policy partitioned by currency alone would answer "the rate of the dollar" with a
 * row that never spoke about the base at all.
 */
class ExchangeRateDaoTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    @AfterTest fun tearDown() = db.close()

    private val dao = db.exchangeRateDao()

    private val march = LocalDate(2026, 3, 14)
    private val february = LocalDate(2026, 2, 1)

    private suspend fun seed(
        currency: String,
        counterCurrency: String,
        date: LocalDate,
        rate: Double,
        source: ExchangeRateEntity.Source = ExchangeRateEntity.Source.DERIVED,
    ) = dao.insert(
        ExchangeRateEntity(
            currency = currency,
            counterCurrency = counterCurrency,
            date = date,
            rate = rate,
            source = source,
        )
    )

    @Test
    fun `the user's beats the derived one of the same date`() = runTest {
        seed("USD", "BRL", march, 5.5, ExchangeRateEntity.Source.DERIVED)
        seed("USD", "BRL", march, 5.6, ExchangeRateEntity.Source.USER)

        assertEquals(5.6, dao.rateOfPairAsOf("USD", "BRL", march)?.rate)
        assertEquals(5.6, dao.ratesAsOf(march).single().rate)
    }

    @Test
    fun `the last date on or before wins over an earlier one`() = runTest {
        seed("USD", "BRL", february, 5.0)
        seed("USD", "BRL", march, 5.5)

        assertEquals(5.5, dao.rateOfPairAsOf("USD", "BRL", march)?.rate)
        assertEquals(5.0, dao.rateOfPairAsOf("USD", "BRL", february)?.rate)
    }

    @Test
    fun `a date the archive does not reach answers nothing`() = runTest {
        seed("USD", "BRL", march, 5.5)

        assertNull(dao.rateOfPairAsOf("USD", "BRL", february))
    }

    @Test
    fun `both directions of the same pair coexist as two observations`() = runTest {
        seed("USD", "BRL", march, 5.5)
        seed("BRL", "USD", march, 0.18)

        assertEquals(5.5, dao.rateOfPairAsOf("USD", "BRL", march)?.rate)
        assertEquals(0.18, dao.rateOfPairAsOf("BRL", "USD", march)?.rate)
        assertEquals(2, dao.ratesAsOf(march).size)
    }

    /**
     * The partition, stated: the dollar priced in euros must not answer the question the
     * dollar priced in reais was asked.
     */
    @Test
    fun `the policy is partitioned by pair and not by currency`() = runTest {
        seed("USD", "BRL", february, 5.0)
        seed("USD", "EUR", march, 0.92)

        assertEquals(5.0, dao.rateOfPairAsOf("USD", "BRL", march)?.rate)
        assertEquals(
            setOf(5.0, 0.92),
            dao.ratesAsOf(march).map { it.rate }.toSet(),
            "a later observation of another pair must not evict this one",
        )
    }
}
