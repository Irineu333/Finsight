package com.neoutils.finsight.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.neoutils.finsight.database.migration.Migration11To12
import com.neoutils.finsight.database.migration.Migration12To13
import com.neoutils.finsight.domain.model.CURRENCY_SEED
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The silent regression the shrink from twenty-two currencies to six could have caused:
 * a user who already holds one of the sixteen that left.
 *
 * They must not be able to tell anything happened. The currency exists as a row, it is
 * **offered** (not archived), and every figure of their account is exactly what it was —
 * which it has to be, since the seeding writes one table and touches no entry.
 */
class CurrencyOutOfSeedMigrationTest {

    private lateinit var connection: SQLiteConnection

    @BeforeTest
    fun setup() {
        connection = BundledSQLiteDriver().open(":memory:")
        V11_SCHEMA.forEach(connection::execSQL)
        Migration11To12(baseCurrency = "BRL").migrate(connection)
    }

    @AfterTest
    fun teardown() {
        connection.close()
    }

    /** A currency of the old catalog that the seed of six no longer brings. */
    private val outOfSeed = "PEN"

    private fun seedDatabase() {
        connection.execSQL(
            "INSERT INTO `accounts` " +
                "(`id`, `name`, `type`, `currency`, `iconKey`, `isDefault`, `createdAt`, `isArchived`) " +
                "VALUES (1, 'Cuenta', 'ASSET', '$outOfSeed', 'wallet', 1, 0, 0)"
        )
        // The nominal the expense lands on — the ledger balances per currency, and the
        // migration verifies exactly that before it commits.
        connection.execSQL(
            "INSERT INTO `accounts` " +
                "(`id`, `name`, `type`, `currency`, `iconKey`, `isDefault`, `createdAt`, `isArchived`) " +
                "VALUES (2, 'Gastos', 'EXPENSE', '$outOfSeed', 'wallet', 0, 0, 0)"
        )
        connection.execSQL(
            "INSERT INTO `transactions` (`id`, `title`, `date`) VALUES (1, 'Almuerzo', '2026-03-10')"
        )
        connection.execSQL(
            "INSERT INTO `entries` (`transactionId`, `accountId`, `amount`, `currency`) " +
                "VALUES (1, 1, -4500, '$outOfSeed'), (1, 2, 4500, '$outOfSeed')"
        )
    }

    private fun balanceOfAccount(): Long {
        val stmt = connection.prepare(
            "SELECT COALESCE(SUM(`amount`), 0) FROM `entries` WHERE `accountId` = 1"
        )
        stmt.step()
        val balance = stmt.getLong(0)
        stmt.close()
        return balance
    }

    private fun offeredCodes(): List<String> {
        val stmt = connection.prepare(
            "SELECT `code` FROM `currencies` WHERE `isArchived` = 0 ORDER BY `code`"
        )
        val out = mutableListOf<String>()
        while (stmt.step()) out += stmt.getText(0)
        stmt.close()
        return out
    }

    @Test
    fun `a currency out of the seed but in use survives the upgrade, offered`() {
        seedDatabase()
        val before = balanceOfAccount()

        Migration12To13(testSeeding()).migrate(connection)

        assertTrue(outOfSeed in offeredCodes(), "the currency in use has to exist, and be offered")
        assertTrue(
            outOfSeed !in CURRENCY_SEED.map { it.code },
            "the fixture only means something while this currency is outside the seed",
        )
        assertEquals(before, balanceOfAccount(), "no figure of the account may move")
        assertEquals(-4500L, balanceOfAccount())
    }

    /** The seeding writes one table. Nothing of the ledger is read as anything but input. */
    @Test
    fun `no entry and no account is touched`() {
        seedDatabase()

        Migration12To13(testSeeding()).migrate(connection)

        val stmt = connection.prepare(
            "SELECT `currency`, `isArchived` FROM `accounts` WHERE `id` = 1"
        )
        stmt.step()
        assertEquals(outOfSeed, stmt.getText(0))
        assertEquals(0L, stmt.getLong(1))
        stmt.close()
    }
}
