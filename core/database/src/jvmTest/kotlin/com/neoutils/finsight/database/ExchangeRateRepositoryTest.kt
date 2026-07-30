package com.neoutils.finsight.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.repository.ExchangeRateRepository
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The one policy for choosing among rates: **the last one on or before** the date asked
 * about, and the user's own over a collected one on the same day.
 *
 * The first half is what keeps the past still. Without it, December's figures are recomputed
 * at today's rate and move on their own when a rate is entered — a closed month that changes
 * because of something that happened after it.
 */
class ExchangeRateRepositoryTest {

    private val file: File = File.createTempFile("finsight-rates", ".db").also { it.delete() }

    @AfterTest
    fun tearDown() {
        file.delete()
    }

    @Test
    fun `the rate in force is the last one on or before the date, never a later one`() = runTest {
        val rates = repository()
        rates.record(rate("USD", "2026-01-10", 5.00))
        rates.record(rate("USD", "2026-03-10", 6.00))

        assertEquals(5.00, rates.rateOn("USD", LocalDate.parse("2026-02-20"))?.rate)
        // On the day itself, and every day after it until the next one.
        assertEquals(6.00, rates.rateOn("USD", LocalDate.parse("2026-03-10"))?.rate)
        assertEquals(6.00, rates.rateOn("USD", LocalDate.parse("2026-12-31"))?.rate)
    }

    @Test
    fun `a rate entered later never reaches back into a date it predates`() = runTest {
        val rates = repository()
        rates.record(rate("USD", "2026-03-10", 6.00))

        // February has no rate at all — which is a defined state, not an error: the figure
        // gains a term of its own rather than a guessed value.
        assertNull(rates.rateOn("USD", LocalDate.parse("2026-02-20")))
    }

    @Test
    fun `the user's rate prevails over the one collected from an operation on the same day`() = runTest {
        val rates = repository()
        rates.record(rate("USD", "2026-05-01", 5.50, ExchangeRate.Source.OPERATION))
        rates.record(rate("USD", "2026-05-01", 5.40, ExchangeRate.Source.USER))

        val chosen = rates.rateOn("USD", LocalDate.parse("2026-05-01"))

        assertEquals(5.40, chosen?.rate)
        assertEquals(ExchangeRate.Source.USER, chosen?.source)
        // Both are kept: the origin is part of the key, so neither silently overwrites the
        // other, and removing the user's own uncovers the collected one again.
        assertEquals(2, rates.getAll().size)
    }

    @Test
    fun `re-recording the same currency, date and origin replaces it instead of duplicating`() = runTest {
        val rates = repository()
        rates.record(rate("USD", "2026-05-01", 5.50, ExchangeRate.Source.OPERATION))
        rates.record(rate("USD", "2026-05-01", 5.60, ExchangeRate.Source.OPERATION))

        assertEquals(listOf(5.60), rates.getAll().map { it.rate })
    }

    @Test
    fun `a currency nobody entered a rate for has none, in any direction`() = runTest {
        val rates = repository()
        rates.record(rate("USD", "2026-05-01", 5.50))

        assertNull(rates.rateOn("EUR", LocalDate.parse("2026-05-01")))
    }

    private fun rate(
        currency: String,
        date: String,
        value: Double,
        source: ExchangeRate.Source = ExchangeRate.Source.USER,
    ) = ExchangeRate(currency = currency, date = LocalDate.parse(date), rate = value, source = source)

    private fun repository(): IExchangeRateRepository = ExchangeRateRepository(
        Room.databaseBuilder<AppDatabase>(name = file.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
            .exchangeRateDao()
    )
}
