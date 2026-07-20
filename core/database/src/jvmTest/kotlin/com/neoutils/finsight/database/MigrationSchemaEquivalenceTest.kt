package com.neoutils.finsight.database

import androidx.room.Room
import androidx.sqlite.SQLiteConnection
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
 * nullability mismatch on `categories.accountId` once survived a fully green
 * suite and would have thrown `Migration didn't properly handle categories` on
 * every real 7 → 9 upgrade. This test fails on any such divergence, whatever the
 * column, index or foreign key.
 */
class MigrationSchemaEquivalenceTest {

    private val file: File = File.createTempFile("finsight-migration", ".db").also { it.delete() }

    @AfterTest
    fun tearDown() {
        file.delete()
    }

    @Test
    fun `Room accepts the schema the migration produces`() = runTest {
        BundledSQLiteDriver().open(file.absolutePath).use { connection ->
            buildV7(connection)
            connection.execSQL("PRAGMA user_version = 7")
        }

        val database = Room.databaseBuilder<AppDatabase>(name = file.absolutePath)
            .addMigrations(MIGRATION_7_9)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

        // Room runs the migration and validates the result against the entities on
        // first access; a divergence throws here instead of on a user's phone.
        database.accountDao().getAllLedgerAccounts()

        assertEquals(emptyList(), database.transactionDao().getAll())
        database.close()
    }
}

private fun buildV7(connection: SQLiteConnection) {
    V7_SCHEMA.forEach(connection::execSQL)
}
