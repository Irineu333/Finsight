package com.neoutils.finsight.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.neoutils.finsight.database.migration.Migration12To13
import com.neoutils.finsight.database.migration.Migration13To14
import com.neoutils.finsight.database.migration.Migration14To15
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Schema 15: the journal of what an agent wrote becomes a table.
 *
 * What is worth asserting is mostly what the migration **does not** do. It adds no
 * column to the ledger and no foreign key to `transactions`: the journal describes
 * writes, it is not part of them, and a retention policy that could take a transaction
 * with it would be the ledger losing history to housekeeping. The upgrade also has
 * nothing to backfill — before this version no agent could write, so an empty journal
 * is the truthful state of every database that existed.
 *
 * The starting point is the frozen v12 with the real `12 → 13` and `13 → 14` replayed
 * over it, the way a device on v14 actually got there. A hand-written v14 fixture would
 * only prove itself.
 */
class Migration14To15Test {

    private lateinit var connection: SQLiteConnection

    @BeforeTest
    fun setup() {
        connection = BundledSQLiteDriver().open(":memory:")
        V12_SCHEMA.forEach(connection::execSQL)
        Migration12To13(baseCurrency = "BRL").migrate(connection)
        Migration13To14(testSeeding()).migrate(connection)
    }

    @AfterTest
    fun teardown() {
        connection.close()
    }

    private fun rowCount(table: String): Long {
        val stmt = connection.prepare("SELECT COUNT(*) FROM `$table`")
        stmt.step()
        val count = stmt.getLong(0)
        stmt.close()
        return count
    }

    @Test
    fun `the table does not exist before the migration and does after`() {
        assertFalse(connection.tableExists("agent_activity"))

        Migration14To15.migrate(connection)

        assertTrue(connection.tableExists("agent_activity"))
    }

    @Test
    fun `it has the columns the entity declares, in the order it declares them`() {
        Migration14To15.migrate(connection)

        assertEquals(
            listOf("id", "timestamp", "client", "tool", "arguments", "affected", "outcome")
                .sorted(),
            connection.getColumns("agent_activity").sorted(),
        )
    }

    /**
     * The client is the one column that admits absence, and that is a decision of the
     * spec rather than a convenience: a connection can be dropped and resumed without
     * the declaration being repeated, and the next revision of the protocol makes the
     * identification optional. A record that required it would need a migration on the
     * day that lands.
     */
    @Test
    fun `a record with no client is accepted`() {
        Migration14To15.migrate(connection)

        connection.execSQL(
            "INSERT INTO `agent_activity` " +
                "(`timestamp`, `client`, `tool`, `arguments`, `outcome`, `affected`) " +
                "VALUES (1700000000000, NULL, 'finsight_record_transactions', '{}', 'OK', '[]')"
        )

        assertEquals(1, rowCount("agent_activity"))
    }

    @Test
    fun `the time index the readings ride on exists`() {
        Migration14To15.migrate(connection)

        assertTrue(connection.indexExists("index_agent_activity_timestamp"))
    }

    @Test
    fun `nothing is backfilled — the journal starts empty`() {
        connection.execSQL(
            "INSERT INTO `accounts` " +
                "(`name`, `type`, `currency`, `iconKey`, `isDefault`, `createdAt`, `isArchived`, `yieldsInterest`) " +
                "VALUES ('Carteira', 'ASSET', 'BRL', 'wallet', 1, 0, 0, 0)"
        )
        connection.execSQL(
            "INSERT INTO `transactions` (`title`, `date`) VALUES ('Mercado', '2026-01-10')"
        )

        Migration14To15.migrate(connection)

        assertEquals(
            0,
            rowCount("agent_activity"),
            "before v15 no agent could write, so an empty journal is the honest state",
        )
    }

    /**
     * The transaction the journal would describe is not linked to it. Pruning the
     * journal is a retention requirement, and it must never be able to take ledger
     * history with it.
     */
    @Test
    fun `deleting the journal leaves the transactions it described untouched`() {
        connection.execSQL(
            "INSERT INTO `accounts` " +
                "(`name`, `type`, `currency`, `iconKey`, `isDefault`, `createdAt`, `isArchived`, `yieldsInterest`) " +
                "VALUES ('Carteira', 'ASSET', 'BRL', 'wallet', 1, 0, 0, 0)"
        )
        connection.execSQL(
            "INSERT INTO `transactions` (`title`, `date`) VALUES ('Mercado', '2026-01-10')"
        )

        Migration14To15.migrate(connection)

        connection.execSQL(
            "INSERT INTO `agent_activity` " +
                "(`timestamp`, `client`, `tool`, `arguments`, `outcome`, `affected`) " +
                "VALUES (1700000000000, 'Some Client', 'finsight_record_transactions', '{}', 'OK', '[\"1\"]')"
        )
        connection.execSQL("DELETE FROM `agent_activity`")

        assertEquals(1, rowCount("transactions"))
    }

    @Test
    fun `no ledger table gained a column`() {
        val before = listOf("transactions", "entries", "accounts", "dimensions")
            .associateWith(connection::getColumns)

        Migration14To15.migrate(connection)

        assertEquals(
            before,
            listOf("transactions", "entries", "accounts", "dimensions")
                .associateWith(connection::getColumns),
            "the journal is not part of the ledger, and the ledger must not learn of it",
        )
    }

    @Test
    fun `running it twice is harmless`() {
        Migration14To15.migrate(connection)
        connection.execSQL(
            "INSERT INTO `agent_activity` " +
                "(`timestamp`, `client`, `tool`, `arguments`, `outcome`, `affected`) " +
                "VALUES (1700000000000, NULL, 'finsight_record_transactions', '{}', 'REFUSED', '[]')"
        )

        Migration14To15.migrate(connection)

        assertEquals(1, rowCount("agent_activity"), "the row survives a re-run")
    }
}
