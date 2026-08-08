package com.neoutils.finsight.database

import androidx.room.Room
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.neoutils.finsight.database.migration.Migration11To12
import com.neoutils.finsight.database.migration.Migration12To13
import com.neoutils.finsight.database.migration.Migration13To14
import com.neoutils.finsight.domain.model.CURRENCY_SEED
import com.neoutils.finsight.domain.model.SeedCurrency
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest

/**
 * Schema 13: the offered set of currencies becomes a table, seeded in **one** write.
 *
 * The claim worth testing is not that six rows appear. It is that the seeding and the
 * legacy relabel of [Migration11To12] fit together **without knowing each other**: the
 * relabel is `10 → 11` and this can only be `12 → 13`, so on an upgrade from v10 the
 * relabel runs before this table exists. No ordering could fix that — and none is needed,
 * because this reads `SELECT DISTINCT currency FROM accounts`, which is what the relabel
 * wrote.
 *
 * A fresh install runs no migration at all, so it is covered through Room itself, against
 * the callback that is the seeding's second entry point onto the same write.
 */
class Migration13To14Test {

    private lateinit var connection: SQLiteConnection

    @BeforeTest
    fun setup() {
        connection = BundledSQLiteDriver().open(":memory:")
        V12_SCHEMA.forEach(connection::execSQL)
        // v12 derived by running the real `11 → 12` over the frozen v11: a device on v12
        // got there this way, and a hand-written fixture would only prove itself.
        Migration12To13(baseCurrency = "BRL").migrate(connection)
    }

    @AfterTest
    fun teardown() {
        connection.close()
    }

    private fun account(name: String, currency: String) {
        connection.execSQL(
            "INSERT INTO `accounts` " +
                "(`name`, `type`, `currency`, `iconKey`, `isDefault`, `createdAt`, `isArchived`, `yieldsInterest`) " +
                "VALUES ('$name', 'ASSET', '$currency', 'wallet', 0, 0, 0, 0)"
        )
    }

    private fun currencies(): Map<String, String> {
        val stmt = connection.prepare("SELECT `code`, `symbol` FROM `currencies` ORDER BY `code`")
        val out = linkedMapOf<String, String>()
        while (stmt.step()) out[stmt.getText(0)] = stmt.getText(1)
        stmt.close()
        return out
    }

    private fun names(): List<String?> {
        val stmt = connection.prepare("SELECT `name` FROM `currencies`")
        val out = mutableListOf<String?>()
        while (stmt.step()) out += if (stmt.isNull(0)) null else stmt.getText(0)
        stmt.close()
        return out
    }

    @Test
    fun `the seed is what an empty database gets`() {
        Migration13To14(testSeeding()).migrate(connection)

        assertEquals(CURRENCY_SEED.map { it.code }.sorted(), currencies().keys.toList())
        assertEquals(
            CURRENCY_SEED.associate { it.code to it.symbol },
            currencies(),
            "the seed's own glyph is what is stored, not the code",
        )
    }

    /** The currency of last resort belongs to the seed, or the base could resolve to nothing. */
    @Test
    fun `the currency of last resort is one of the seeded rows`() {
        Migration13To14(testSeeding()).migrate(connection)

        assertTrue("USD" in currencies())
    }

    @Test
    fun `nobody loses the currency they already use`() {
        account("Cuenta", "ARS")
        account("Cuenta 2", "ARS")
        account("Soles", "PEN")

        Migration13To14(testSeeding()).migrate(connection)

        assertTrue("ARS" in currencies(), "a currency out of the seed but in use has to survive")
        assertTrue("PEN" in currencies())
        assertEquals(CURRENCY_SEED.size + 2, currencies().size, "and only once, however many accounts")
    }

    @Test
    fun `the device's currency arrives by the same write, not by a mechanism of its own`() {
        Migration13To14(testSeeding(locale = SeedCurrency("PLN", "zł"))).migrate(connection)

        assertEquals("zł", currencies()["PLN"])
    }

    /**
     * The whole of D9: no ordering is arranged, and none is needed. The relabel writes
     * `accounts.currency` two schemas earlier, and the seeding picks it up because it
     * reads that column — neither migration names the other.
     */
    @Test
    fun `what the relabel denominated is collected by the seeding`() {
        val fresh = BundledSQLiteDriver().open(":memory:")
        try {
            V11_SCHEMA.forEach(fresh::execSQL)
            fresh.execSQL(
                "INSERT INTO `accounts` " +
                    "(`name`, `type`, `currency`, `iconKey`, `isDefault`, `createdAt`, `isArchived`, `yieldsInterest`) " +
                    "VALUES ('Carteira', 'ASSET', 'BRL', 'wallet', 1, 0, 0, 0)"
            )

            // A device in Chile: the relabel re-denominates the legacy chart to CLP, and
            // CLP is in no seed anywhere.
            Migration11To12(relabelCurrency = "CLP").migrate(fresh)
            Migration12To13(baseCurrency = "CLP").migrate(fresh)
            Migration13To14(testSeeding()).migrate(fresh)

            val stmt = fresh.prepare("SELECT `code` FROM `currencies` WHERE `code` = 'CLP'")
            assertTrue(stmt.step(), "the relabelled currency has to exist as a row")
            stmt.close()
        } finally {
            fresh.close()
        }
    }

    /**
     * `name` stays null. Storing it would freeze it in the language of the run that
     * seeded it, and switching the app's language would silently stop translating it.
     */
    @Test
    fun `no seeded row stores a name`() {
        account("Cuenta", "ARS")

        Migration13To14(testSeeding(locale = SeedCurrency("PLN", "zł"))).migrate(connection)

        assertTrue(names().all { it == null })
    }

    @Test
    fun `running the seeding twice writes nothing the second time`() {
        Migration13To14(testSeeding()).migrate(connection)
        val first = currencies()

        connection.execSQL("UPDATE `currencies` SET `symbol` = 'X' WHERE `code` = 'BRL'")
        Migration13To14(testSeeding()).migrate(connection)

        assertEquals(first.keys, currencies().keys)
        assertEquals("X", currencies()["BRL"], "an existing row is not overwritten by the seed")
    }

    /**
     * A fresh install runs no migration: Room creates the schema from the entities. The
     * callback is the seeding's second entry point onto the *same* write, and without it
     * the only user with no currencies at all would be the one who just installed.
     */
    @Test
    fun `a fresh install is seeded too`() = runTest {
        val file = File.createTempFile("finsight-seed", ".db").also { it.delete() }
        try {
            val database = Room.databaseBuilder<AppDatabase>(name = file.absolutePath)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.Unconfined)
                .let {
                    getRoomDatabase(
                        builder = it,
                        baseCurrency = "BRL",
                        currencySeeding = testSeeding(locale = SeedCurrency("PLN", "zł")),
                    )
                }

            val codes = database.currencyDao().getAll().map { it.code }
            assertTrue(CURRENCY_SEED.map { it.code }.all { it in codes })
            assertTrue("PLN" in codes)
            database.close()
        } finally {
            file.delete()
        }
    }
}
