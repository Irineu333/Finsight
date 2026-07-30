package com.neoutils.finsight.database

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
        // the moment `AppDatabase` became 11, a v7 device stopped being migratable by
        // `MIGRATION_7_10` alone, and this test is what says so before a user does.
        val database = openWith(MIGRATION_7_10, migration1011())

        // Room runs the migrations and validates the result against the entities on
        // first access; a divergence throws here instead of on a user's phone.
        database.accountDao().getAllLedgerAccounts()

        assertEquals(emptyList(), database.transactionDao().getAll())
        assertEquals(emptyList(), database.exchangeRateDao().getByCurrency("USD"))
        database.close()
    }

    @Test
    fun `Room accepts the schema the migration produces from v10`() = runTest {
        BundledSQLiteDriver().open(file.absolutePath).use { connection ->
            V10_SCHEMA.forEach(connection::execSQL)
            connection.execSQL("PRAGMA user_version = 10")
        }

        val database = openWith(migration1011())

        // The identity-hash check that would otherwise fail on the device: the rate
        // table the migration writes by hand and the one the entity declares have to be
        // the same table, index for index — and `budgets.currency`, appended by ALTER,
        // has to satisfy a column the entity declares in the middle of its list.
        database.accountDao().getAllLedgerAccounts()
        assertEquals(emptyList(), database.exchangeRateDao().getByCurrency("USD"))
        database.close()
    }

    private fun openWith(vararg migrations: Migration): AppDatabase =
        Room.databaseBuilder<AppDatabase>(name = file.absolutePath)
            .addMigrations(*migrations)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
}
