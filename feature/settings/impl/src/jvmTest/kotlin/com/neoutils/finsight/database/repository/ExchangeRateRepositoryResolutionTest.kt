package com.neoutils.finsight.database.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.mapper.ExchangeRateMapper
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The scenario the whole change exists for, over a real database: with a base of BRL and
 * an archive holding only `(USD, BRL)` and `(EUR, BRL)`, switching the base to USD makes
 * the euro resolve by triangulation over the real and the real by the inverse — **with
 * no row created, altered or removed**.
 */
class ExchangeRateRepositoryResolutionTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    @AfterTest fun tearDown() = db.close()

    private class MovableBase(base: String) : IBaseCurrencyRepository {
        private val state = MutableStateFlow(base)
        override fun observe(): StateFlow<String> = state
        override suspend fun set(code: String) { state.value = code }
    }

    private val base = MovableBase("BRL")

    private val repository = ExchangeRateRepository(
        dao = db.exchangeRateDao(),
        mapper = ExchangeRateMapper(),
        baseCurrencyRepository = base,
    )

    private val march = LocalDate(2026, 3, 14)

    private suspend fun seedArchive() {
        repository.save(rate("USD", "BRL", 5.5))
        repository.save(rate("EUR", "BRL", 6.0))
    }

    private fun rate(from: String, to: String, value: Double) = ExchangeRate(
        currency = from,
        counterCurrency = to,
        date = march,
        rate = value,
        source = ExchangeRate.Source.USER,
    )

    private fun assertClose(expected: Double, actual: Double?, message: String? = null) {
        assertTrue(actual != null && abs(expected - actual) < 1e-9, message ?: "expected ~$expected but was $actual")
    }

    @Test
    fun `switching the base re-expresses the archive without touching a row`() = runTest {
        seedArchive()

        val before = db.exchangeRateDao().observeAllOnce()

        assertClose(5.5, repository.ratesAsOf(march)["USD"]?.rate)
        assertClose(6.0, repository.ratesAsOf(march)["EUR"]?.rate)

        base.set("USD")

        val after = repository.ratesAsOf(march)
        // The euro over the real, by triangulation; the real itself, by the inverse.
        assertClose(6.0 / 5.5, after["EUR"]?.rate)
        assertClose(1.0 / 5.5, after["BRL"]?.rate)
        assertNull(after["USD"], "the base is not a term of itself")

        assertEquals(before, db.exchangeRateDao().observeAllOnce(), "a stored row moved")
    }

    @Test
    fun `switching back gives exactly the figures of before`() = runTest {
        seedArchive()

        val before = repository.ratesAsOf(march).mapValues { it.value.rate }

        base.set("USD")
        base.set("BRL")

        assertEquals(before, repository.ratesAsOf(march).mapValues { it.value.rate })
    }

    @Test
    fun `a base the archive does not reach degrades rather than inventing a rate`() = runTest {
        seedArchive()

        base.set("JPY")

        assertEquals(emptyMap(), repository.ratesAsOf(march))
        assertNull(repository.rateAsOf("USD", march))
    }

    /** The direct observation answers with the stored row itself, origin included. */
    @Test
    fun `the direct level answers with the row that was stored`() = runTest {
        seedArchive()

        val direct = repository.rateBetween("USD", "BRL", march)

        assertEquals(ExchangeRate.Source.USER, direct?.source)
        assertTrue((direct?.id ?: 0) > 0, "a stored observation keeps its identity")
    }

    /** The inverse is the same observation read backwards, so it keeps its origin. */
    @Test
    fun `the answer by inverse of a remote row declares REMOTE`() = runTest {
        repository.save(rate("BRL", "USD", 0.18).copy(source = ExchangeRate.Source.REMOTE))

        val answer = repository.rateBetween("USD", "BRL", march)

        assertClose(1.0 / 0.18, answer?.rate)
        assertEquals(ExchangeRate.Source.REMOTE, answer?.source)
        assertEquals(0, answer?.id, "an implied answer is no row, and may not return to the archive")
    }

    @Test
    fun `a triangulation over a USER and a REMOTE leg declares REMOTE`() = runTest {
        repository.save(rate("EUR", "BRL", 6.0).copy(source = ExchangeRate.Source.USER))
        repository.save(rate("USD", "BRL", 5.5).copy(source = ExchangeRate.Source.REMOTE))

        assertEquals(
            ExchangeRate.Source.REMOTE,
            repository.rateBetween("EUR", "USD", march)?.source,
        )
    }

    @Test
    fun `a triangulation over a REMOTE and a DERIVED leg declares DERIVED`() = runTest {
        repository.save(rate("EUR", "BRL", 6.0).copy(source = ExchangeRate.Source.REMOTE))
        repository.save(rate("USD", "BRL", 5.5).copy(source = ExchangeRate.Source.DERIVED))

        assertEquals(
            ExchangeRate.Source.DERIVED,
            repository.rateBetween("EUR", "USD", march)?.source,
            "the weakest leg is what the answer may honestly claim",
        )
    }

    /** One row per pair, elected by the archive's own policy — not a reduction up here. */
    @Test
    fun `the in-force view answers one row per pair`() = runTest {
        repository.save(rate("USD", "BRL", 5.5).copy(source = ExchangeRate.Source.DERIVED))
        repository.save(rate("USD", "BRL", 5.4).copy(source = ExchangeRate.Source.REMOTE))
        repository.save(rate("EUR", "BRL", 6.0).copy(source = ExchangeRate.Source.REMOTE))

        val inForce = repository.observeInForce(march).first()

        assertEquals(2, inForce.size)
        assertClose(5.4, inForce.single { it.currency == "USD" }.rate)
        assertEquals(ExchangeRate.Source.REMOTE, inForce.single { it.currency == "USD" }.source)
    }
}

private suspend fun com.neoutils.finsight.database.dao.ExchangeRateDao.observeAllOnce() =
    observeAll().first()
