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
import kotlin.test.assertTrue

/**
 * Schema 15: the agent activity log becomes a table.
 *
 * The claim worth testing is almost entirely a negative one. The table is new, born empty, and
 * read by nothing that already exists — so what has to be proven is that **nothing else moved**.
 * The fixture is therefore populated across the ledger and the facades, and the whole database
 * is compared row for row on either side of the migration.
 *
 * The v14 the migration runs against is derived by running the real `12 → 13` and `13 → 14` over
 * the frozen v12: a hand-written v14 fixture would only prove itself.
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

    /**
     * A ledger with one balanced transaction, the facades that classify it, and a rate — enough
     * that every table the migration must not touch has something in it to be moved.
     */
    private fun seedLedger() {
        connection.execSQL(
            "INSERT INTO `accounts` " +
                "(`id`, `name`, `type`, `currency`, `iconKey`, `isDefault`, `createdAt`, `isArchived`, `yieldsInterest`) " +
                "VALUES (1, 'Carteira', 'ASSET', 'BRL', 'wallet', 1, 1000, 0, 0), " +
                "(2, 'Despesas', 'EXPENSE', 'BRL', 'wallet', 0, 1000, 0, 0)"
        )
        connection.execSQL("INSERT INTO `dimensions` (`id`, `kind`) VALUES (1, 'CATEGORY')")
        connection.execSQL(
            "INSERT INTO `categories` " +
                "(`id`, `name`, `iconKey`, `type`, `createdAt`, `dimensionId`, `isArchived`, `systemKey`) " +
                "VALUES (1, 'Mercado', 'food', 'EXPENSE', 1000, 1, 0, NULL)"
        )
        connection.execSQL(
            "INSERT INTO `transactions` (`id`, `title`, `date`) VALUES (1, 'Feira', '2026-01-10')"
        )
        connection.execSQL(
            "INSERT INTO `entries` (`transactionId`, `accountId`, `amount`, `currency`, `dimensionId`) " +
                "VALUES (1, 2, 5000, 'BRL', 1), (1, 1, -5000, 'BRL', NULL)"
        )
        connection.execSQL(
            "INSERT INTO `exchange_rates` (`currency`, `counterCurrency`, `date`, `rate`, `source`) " +
                "VALUES ('USD', 'BRL', '2026-01-10', 5.5, 'USER')"
        )
    }

    private fun activityCount(): Long {
        val stmt = connection.prepare("SELECT COUNT(*) FROM `agent_activity`")
        stmt.step()
        val count = stmt.getLong(0)
        stmt.close()
        return count
    }

    @Test
    fun `the log arrives as a table, and it arrives empty`() {
        Migration14To15.migrate(connection)

        assertTrue(connection.tableExists("agent_activity"))
        assertEquals(0L, activityCount(), "a device that never ran an agent has nothing to show")
    }

    /**
     * The columns the requirement names, one for one: when, which operation, what it was about
     * in readable terms, how it ended, and the reference that reaches what it produced.
     */
    @Test
    fun `the log records when, what, about what, how it ended and what it reached`() {
        Migration14To15.migrate(connection)

        assertEquals(
            listOf("id", "at", "operation", "summary", "outcome", "detail", "referenceKind", "referenceId"),
            connection.getColumns("agent_activity"),
        )
    }

    /**
     * There is no column saying whether an act was a read, because a read never becomes a row.
     * A column would invite recording queries and then filtering them out, which is the same
     * flood with an extra step.
     */
    @Test
    fun `nothing in the log distinguishes a read, because a read is never written`() {
        Migration14To15.migrate(connection)

        assertTrue(connection.getColumns("agent_activity").none { it.contains("read", ignoreCase = true) })
    }

    /**
     * The reference is two plain columns and no foreign key. The log must never be the reason a
     * posting cannot be deleted, so a reference is allowed to name something that has since
     * stopped existing.
     */
    @Test
    fun `the reference to what was produced is not a foreign key`() {
        Migration14To15.migrate(connection)

        val stmt = connection.prepare("PRAGMA foreign_key_list(`agent_activity`)")
        val keys = mutableListOf<String>()
        while (stmt.step()) keys += stmt.getText(2)
        stmt.close()

        assertEquals(emptyList<String>(), keys)
    }

    /** Both reads and both halves of the retention policy order by `at`, so it is indexed. */
    @Test
    fun `the column every read orders by is indexed`() {
        Migration14To15.migrate(connection)

        assertTrue(connection.indexExists("index_agent_activity_at"))
    }

    /**
     * The strongest thing this migration claims: every row of every table it found is the row it
     * left. Not a sample of columns — the whole database, compared value for value.
     */
    @Test
    fun `not one existing value is altered`() {
        seedLedger()
        val before = connection.dumpAllTables()
        assertTrue(before.getValue("entries").size == 2, "the fixture has to have something to move")
        assertTrue(before.getValue("currencies").isNotEmpty())

        Migration14To15.migrate(connection)

        val after = connection.dumpAllTables()
        assertEquals(before.keys + "agent_activity", after.keys, "one table appears, and only one")
        before.keys.forEach { table ->
            assertEquals(before.getValue(table), after.getValue(table), "table `$table` was altered")
        }
    }

    /** And nothing that already existed is dropped, rebuilt or renamed along the way. */
    @Test
    fun `no table or index that existed is removed`() {
        seedLedger()
        val before = connection.schemaObjectNames()

        Migration14To15.migrate(connection)

        val after = connection.schemaObjectNames()
        assertTrue(after.containsAll(before), "missing: ${before - after}")
        assertEquals(
            setOf("table:agent_activity", "index:index_agent_activity_at"),
            after - before,
        )
    }

    /**
     * A device that already carries the table — the fresh install, where Room creates the schema
     * from the entities and no migration runs — is not broken by the hop being replayed.
     */
    @Test
    fun `running it over a database that already has the table changes nothing`() {
        seedLedger()
        Migration14To15.migrate(connection)
        connection.execSQL(
            "INSERT INTO `agent_activity` (`at`, `operation`, `summary`, `outcome`) " +
                "VALUES (1000, 'create_transaction', 'Feira de 50,00 na Carteira', 'APPLIED')"
        )
        val before = connection.dumpAllTables()

        Migration14To15.migrate(connection)

        assertEquals(before, connection.dumpAllTables())
    }
}
