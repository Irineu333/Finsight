package com.neoutils.finsight.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.neoutils.finsight.database.migration.Migration12To13
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Schema 12 over the real v11 shape: a rate stops depending on the preference in force
 * to mean anything, and **no stored value moves**.
 *
 * The fill is exact rather than approximate — every existing row was measured against
 * the base in force, which until this schema had no way to change — so the claim worth
 * testing is that `rate`, `date`, `currency` and `source` come out byte for byte, and
 * that the code the migration writes is the one it was handed.
 */
class Migration12To13Test {

    private lateinit var connection: SQLiteConnection

    @BeforeTest
    fun setup() {
        connection = BundledSQLiteDriver().open(":memory:")
        V12_SCHEMA.forEach(connection::execSQL)
    }

    @AfterTest
    fun teardown() {
        connection.close()
    }

    private fun migrate(baseCurrency: String = "BRL") =
        Migration12To13(baseCurrency).migrate(connection)

    private fun seedRate(currency: String, date: String, rate: Double, source: String) {
        connection.execSQL(
            "INSERT INTO `exchange_rates` (`currency`, `date`, `rate`, `source`) " +
                "VALUES ('$currency', '$date', $rate, '$source')"
        )
    }

    private fun rows(): List<List<String>> {
        val stmt = connection.prepare(
            "SELECT `currency`, `counterCurrency`, `date`, `rate`, `source` " +
                "FROM `exchange_rates` ORDER BY `currency`, `date`, `source`"
        )
        val out = mutableListOf<List<String>>()
        while (stmt.step()) {
            out += listOf(stmt.getText(0), stmt.getText(1), stmt.getText(2), stmt.getDouble(3).toString(), stmt.getText(4))
        }
        stmt.close()
        return out
    }

    @Test
    fun `the counterpart column exists and is not nullable`() {
        migrate()

        // Appended, as `ALTER TABLE` does — and the entity declares it in the middle.
        // That the two are still the same table is what
        // `MigrationSchemaEquivalenceTest` asserts, through Room's own identity hash.
        assertEquals(
            listOf("id", "currency", "date", "rate", "source", "counterCurrency"),
            connection.getColumns("exchange_rates"),
        )

        val stmt = connection.prepare("PRAGMA table_info(`exchange_rates`)")
        var notNull = false
        while (stmt.step()) {
            if (stmt.getText(1) == "counterCurrency") notNull = stmt.getLong(3) == 1L
        }
        stmt.close()
        assertTrue(notNull, "counterCurrency has to be NOT NULL — a row without a pair is the defect")
    }

    @Test
    fun `every pre-existing row is denominated in the base it was measured against`() {
        seedRate("USD", "2026-03-10", 5.5, "DERIVED")
        seedRate("EUR", "2026-03-10", 6.0, "USER")
        seedRate("JPY", "2026-01-02", 0.037, "DERIVED")

        migrate()

        assertEquals(listOf("BRL", "BRL", "BRL"), rows().map { it[1] })
    }

    /** The parameter is the parameter, and not a literal hiding behind a default. */
    @Test
    fun `a base that is not BRL is the one written`() {
        seedRate("BRL", "2026-03-10", 0.18, "USER")

        migrate(baseCurrency = "USD")

        assertEquals(listOf("USD"), rows().map { it[1] })
    }

    @Test
    fun `no stored value moves`() {
        seedRate("USD", "2026-03-10", 5.5, "DERIVED")
        seedRate("EUR", "2026-02-01", 6.25, "USER")

        migrate()

        assertEquals(
            listOf(
                listOf("EUR", "BRL", "2026-02-01", "6.25", "USER"),
                listOf("USD", "BRL", "2026-03-10", "5.5", "DERIVED"),
            ),
            rows(),
        )
    }

    @Test
    fun `the unique index follows the pair, and the old one is gone`() {
        migrate()

        assertTrue(connection.indexExists("index_exchange_rates_currency_counterCurrency_date_source"))
        assertTrue(connection.indexExists("index_exchange_rates_currency_counterCurrency_date"))
        assertFalse(connection.indexExists("index_exchange_rates_currency_date_source"))
        assertFalse(connection.indexExists("index_exchange_rates_currency_date"))
    }

    /**
     * What the new index opens: the dollar priced against the real and against the euro
     * on the same day are two observations, and both fit.
     */
    @Test
    fun `the same currency and date in two counterparts now coexist`() {
        migrate()

        connection.execSQL(
            "INSERT INTO `exchange_rates` (`currency`, `counterCurrency`, `date`, `rate`, `source`) " +
                "VALUES ('USD', 'BRL', '2026-03-10', 5.5, 'USER')"
        )
        connection.execSQL(
            "INSERT INTO `exchange_rates` (`currency`, `counterCurrency`, `date`, `rate`, `source`) " +
                "VALUES ('USD', 'EUR', '2026-03-10', 0.92, 'USER')"
        )

        assertEquals(2, rows().size)
    }

    /**
     * `execSQL` binds nothing, so the code is interpolated. It stops before any
     * statement runs rather than reaching one.
     */
    @Test
    fun `a code that is not a code is refused before any statement runs`() {
        assertFailsWith<IllegalArgumentException> { migrate(baseCurrency = "not a code") }

        assertEquals(
            listOf("id", "currency", "date", "rate", "source"),
            connection.getColumns("exchange_rates"),
        )
    }
}
