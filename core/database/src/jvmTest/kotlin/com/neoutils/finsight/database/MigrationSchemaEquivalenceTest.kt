package com.neoutils.finsight.database

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.neoutils.finsight.database.migration.Migration10To11
import com.neoutils.finsight.database.migration.Migration11To12
import com.neoutils.finsight.database.migration.Migration12To13
import com.neoutils.finsight.database.migration.Migration13To14
import com.neoutils.finsight.database.migration.Migration14To15
import com.neoutils.finsight.database.migration.Migration7To10
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest

/**
 * Opens the migrated database through Room itself, which is the only way to run
 * Room's own schema validation — the check that actually happens on a device.
 *
 * Every other migration test asserts individual facts, which is spot-checking: a
 * nullability mismatch on a facade's link column once survived a fully green suite
 * and would have thrown `Migration didn't properly handle categories` on every real
 * upgrade. This test fails on any such divergence, whatever the column, index or
 * foreign key.
 */
class MigrationSchemaEquivalenceTest {

    private val file: File = File.createTempFile("finsight-migration", ".db").also { it.delete() }

    @AfterTest
    fun tearDown() {
        file.delete()
    }

    @Test
    fun `Room accepts the schema the whole chain produces from v7`() = runTest {
        BundledSQLiteDriver().open(file.absolutePath).use { connection ->
            V7_SCHEMA.forEach(connection::execSQL)
            connection.execSQL("PRAGMA user_version = 7")
        }

        // The chain has to reach the *current* version, not the one it used to end at:
        // the moment `AppDatabase` moved past 10, a v7 device stopped being migratable by
        // `Migration7To10` alone, and this test is what says so before a user does.
        val database = openWith(
            Migration7To10,
            Migration10To11,
            Migration11To12(),
            Migration12To13(baseCurrency = "BRL"),
            Migration13To14(testSeeding()),
            Migration14To15,
        )

        // Room runs the migrations and validates the result against the entities on
        // first access; a divergence throws here instead of on a user's phone.
        database.accountDao().getAllLedgerAccounts()

        assertEquals(emptyList(), database.transactionDao().getAll())
        assertEquals(emptyList(), database.exchangeRateDao().getByCurrency("USD"))
        // The activity log arrives created and empty, however far back the device started.
        assertEquals(0, database.agentActivityDao().count())
        database.close()
    }

    @Test
    fun `Room accepts the schema the migration produces from v10`() = runTest {
        BundledSQLiteDriver().open(file.absolutePath).use { connection ->
            V10_SCHEMA.forEach(connection::execSQL)
            connection.execSQL("PRAGMA user_version = 10")
        }

        val database = openWith(
            Migration10To11,
            Migration11To12(),
            Migration12To13(baseCurrency = "BRL"),
            Migration13To14(testSeeding()),
            Migration14To15,
        )

        // The identity-hash check that would otherwise fail on the device: the rate
        // table the migration writes by hand and the one the entity declares have to be
        // the same table, index for index — and `budgets.currency`, appended by ALTER,
        // has to satisfy a column the entity declares in the middle of its list.
        database.accountDao().getAllLedgerAccounts()
        assertEquals(emptyList(), database.exchangeRateDao().getByCurrency("USD"))
        assertEquals(0, database.agentActivityDao().count())
        database.close()
    }

    @Test
    fun `Room accepts the schema the migration produces from v11`() = runTest {
        BundledSQLiteDriver().open(file.absolutePath).use { connection ->
            V11_SCHEMA.forEach(connection::execSQL)
            connection.execSQL("PRAGMA user_version = 11")
        }

        val database = openWith(
            Migration11To12(),
            Migration12To13(baseCurrency = "BRL"),
            Migration13To14(testSeeding()),
            Migration14To15,
        )

        // The identity-hash check for the pair: `counterCurrency`, appended by ALTER,
        // has to satisfy a column the entity declares in the middle of its list, and the
        // two indices the migration rebuilds by hand have to be the two the entity
        // declares — name for name, column for column.
        database.accountDao().getAllLedgerAccounts()
        assertEquals(emptyList(), database.exchangeRateDao().getByCurrency("USD"))
        assertEquals(0, database.agentActivityDao().count())
        database.close()
    }

    private fun openWith(vararg migrations: Migration): AppDatabase =
        Room.databaseBuilder<AppDatabase>(name = file.absolutePath)
            .addMigrations(*migrations)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
}
