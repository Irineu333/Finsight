package com.neoutils.finsight.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.entity.ExchangeRateEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The archive's reading policy, over a real database: **the last observation on or
 * before the date, per pair, ties on that date broken by origin — `USER` ▸ `REMOTE` ▸
 * `DERIVED`.**
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
    fun `the user's beats the other two of the same date`() = runTest {
        seed("USD", "BRL", march, 5.5, ExchangeRateEntity.Source.DERIVED)
        seed("USD", "BRL", march, 5.4, ExchangeRateEntity.Source.REMOTE)
        seed("USD", "BRL", march, 5.6, ExchangeRateEntity.Source.USER)

        assertEquals(5.6, dao.rateOfPairAsOf("USD", "BRL", march)?.rate)
        assertEquals(5.6, dao.ratesAsOf(march).single().rate)
    }

    /**
     * A harvested rate carries what the operation charged and answers *how much it cost*;
     * a quote answers *how much it was worth*. Consolidating is valuing.
     */
    @Test
    fun `the remote quote beats the harvested one of the same date`() = runTest {
        seed("USD", "BRL", march, 5.5, ExchangeRateEntity.Source.DERIVED)
        seed("USD", "BRL", march, 5.4, ExchangeRateEntity.Source.REMOTE)

        assertEquals(5.4, dao.rateOfPairAsOf("USD", "BRL", march)?.rate)
        assertEquals(5.4, dao.ratesAsOf(march).single().rate)
    }

    /**
     * The ranking breaks ties **inside** a date and never over one. A user's correction
     * corrected the day it was an assertion about; it does not pin the pair for ever.
     */
    @Test
    fun `the date beats the origin`() = runTest {
        seed("USD", "BRL", february, 5.6, ExchangeRateEntity.Source.USER)
        seed("USD", "BRL", march, 5.4, ExchangeRateEntity.Source.REMOTE)

        assertEquals(5.4, dao.rateOfPairAsOf("USD", "BRL", march)?.rate)
        assertEquals(5.4, dao.ratesAsOf(march).single().rate)

        assertEquals(
            5.6,
            dao.rateOfPairAsOf("USD", "BRL", february)?.rate,
            "February goes on answering with February's observation",
        )
    }

    /**
     * The `ORDER BY` and the `NOT EXISTS` are the same ranking written twice, and with
     * three origins that has to be checked rather than assumed.
     */
    @Test
    fun `both methods agree pair by pair over the same archive`() = runTest {
        seed("USD", "BRL", february, 5.0, ExchangeRateEntity.Source.USER)
        seed("USD", "BRL", march, 5.5, ExchangeRateEntity.Source.DERIVED)
        seed("USD", "BRL", march, 5.4, ExchangeRateEntity.Source.REMOTE)
        seed("EUR", "BRL", march, 6.1, ExchangeRateEntity.Source.REMOTE)
        seed("EUR", "BRL", march, 6.3, ExchangeRateEntity.Source.USER)
        seed("JPY", "USD", february, 0.0067, ExchangeRateEntity.Source.DERIVED)

        val byPredicate = dao.ratesAsOf(march).associate { (it.currency to it.counterCurrency) to it.rate }

        assertEquals(
            byPredicate,
            byPredicate.keys.associateWith { (currency, counter) ->
                dao.rateOfPairAsOf(currency, counter, march)!!.rate
            },
        )
    }

    /** One row per pair, not per currency: the dollar has two pairs here and one each. */
    @Test
    fun `the in-force view answers one row per pair`() = runTest {
        seed("USD", "BRL", february, 5.0, ExchangeRateEntity.Source.DERIVED)
        seed("USD", "BRL", march, 5.4, ExchangeRateEntity.Source.REMOTE)
        seed("USD", "EUR", march, 0.92, ExchangeRateEntity.Source.REMOTE)
        seed("EUR", "BRL", march, 6.1, ExchangeRateEntity.Source.DERIVED)

        val inForce = dao.observeInForce(march).first()

        assertEquals(
            setOf("USD" to "BRL", "USD" to "EUR", "EUR" to "BRL"),
            inForce.map { it.currency to it.counterCurrency }.toSet(),
        )
        assertEquals(
            5.4,
            inForce.single { it.currency == "USD" && it.counterCurrency == "BRL" }.rate,
            "and each row is the one the policy elects",
        )
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
