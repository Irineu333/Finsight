package com.neoutils.finsight.database

import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.neoutils.finsight.database.entity.CategoryEntity
import com.neoutils.finsight.database.exception.DatabaseRestoreError
import com.neoutils.finsight.database.exception.DatabaseRestoreException
import com.neoutils.finsight.database.snapshot.captureInto
import com.neoutils.finsight.database.snapshot.replaceContentFrom
import com.neoutils.finsight.domain.model.DimensionKind
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * The swap: the live database stops holding what it held and starts holding what the
 * approved file holds, in one transaction, without closing anything.
 *
 * Two properties carry the whole design and are asserted here rather than reasoned
 * about. The order tables are emptied and refilled in is **derived** from the foreign
 * keys the database declares about itself, so a table nobody wrote code for is still
 * handled — a hand-kept list would be remembered at the wrong time, years after the
 * migration that invalidated it. And the swap must not disturb the connection that
 * serves the app: the flows already collecting have to re-emit on their own, because
 * a restore that needs the user to relaunch the app is a restore that lost half its
 * promise on iOS, where relaunching is not ours to do.
 */
class DatabaseRestoreTest {

    private val liveFile: File = File.createTempFile("finsight-restore-live", ".db").also { it.delete() }
    private val backupFile: File = File.createTempFile("finsight-restore-file", ".db").also { it.delete() }

    private fun open(path: String) = getRoomDatabase(
        builder = getDatabaseBuilder(path = path),
        baseCurrency = "BRL",
        currencySeeding = testSeeding(),
    )

    private val live = open(liveFile.absolutePath)

    @AfterTest
    fun tearDown() {
        live.close()
        listOf(liveFile, backupFile).forEach { file ->
            listOf("", "-wal", "-shm").forEach { File(file.absolutePath + it).delete() }
        }
    }

    private suspend fun AppDatabase.seedCategory(name: String): Long {
        val dimensionId = dimensionDao().emit(DimensionKind.CATEGORY)
        return categoryDao().insert(
            CategoryEntity(
                name = name,
                iconKey = "shopping",
                type = CategoryEntity.Type.EXPENSE,
                dimensionId = dimensionId,
            )
        )
    }

    /** A file holding [names], produced the way the app produces one. */
    private suspend fun backupHolding(vararg names: String): File {
        val source = open(backupFile.absolutePath + ".source")
        try {
            names.forEach { source.seedCategory(it) }
            source.captureInto(backupFile.absolutePath, appVersion = "1.2.3", platform = "jvm")
        } finally {
            source.close()
            listOf("", "-wal", "-shm").forEach {
                File(backupFile.absolutePath + ".source" + it).delete()
            }
        }
        return backupFile
    }

    private fun onFile(file: File, block: (SQLiteConnection) -> Unit) {
        val connection = BundledSQLiteDriver().open(file.absolutePath)
        try {
            block(connection)
        } finally {
            connection.close()
        }
    }

    private suspend fun liveCategoryNames(): List<String> =
        live.categoryDao().observeAllCategories().first().map { it.name }

    /**
     * Statements against the live database go through Room's own writer connection.
     * A raw connection on the same file would be a second writer on a database held
     * open in write-ahead logging, and the lock it waits on is not what any of these
     * tests is about.
     */
    private suspend fun execOnLive(sql: String) = live.useWriterConnection { connection ->
        connection.usePrepared(sql) { it.step() }
    }

    private suspend fun countInLive(sql: String): Long = live.useWriterConnection { connection ->
        connection.usePrepared(sql) { statement ->
            statement.step()
            statement.getLong(0)
        }
    }

    @Test
    fun `the archive becomes the one in the file`() = runBlocking {
        live.seedCategory("Wallet")
        val file = backupHolding("Groceries", "Rent")

        live.replaceContentFrom(file.absolutePath)

        assertEquals(
            listOf("Groceries", "Rent"),
            liveCategoryNames().sorted(),
            "nothing of the previous archive survives, and everything of the file arrives",
        )
    }

    @Test
    fun `a flow already collecting re-emits on its own`() = runBlocking {
        live.seedCategory("Wallet")
        val file = backupHolding("Groceries")

        val emissions = Channel<List<String>>(Channel.UNLIMITED)
        val collector = launch(Dispatchers.IO) {
            live.categoryDao().observeAllCategories().collect { categories ->
                emissions.send(categories.map { it.name })
            }
        }
        assertEquals(
            listOf("Wallet"),
            withTimeout(5.seconds) { emissions.receive() },
            "the collector starts from the live archive",
        )

        live.replaceContentFrom(file.absolutePath)

        assertEquals(
            listOf("Groceries"),
            withTimeout(5.seconds) { emissions.receive() },
            "no manual refreshAsync(), no reopening, no relaunch",
        )
        collector.cancel()
    }

    @Test
    fun `a swap landing before the first emission does not strand the flow`() = runBlocking {
        live.seedCategory("Wallet")
        val file = backupHolding("Groceries")

        // Deliberately no wait for the first emission: the collector may not have run
        // its first query when the swap lands. Whichever side of the race wins, the
        // state the flow settles on has to be the restored one — never the previous
        // archive, which would be a screen showing data the database no longer holds.
        val emissions = Channel<List<String>>(Channel.UNLIMITED)
        val collector = launch(Dispatchers.IO) {
            live.categoryDao().observeAllCategories().collect { categories ->
                emissions.send(categories.map { it.name })
            }
        }

        live.replaceContentFrom(file.absolutePath)

        val seen = mutableListOf<List<String>>()
        withTimeout(5.seconds) {
            while (seen.lastOrNull() != listOf("Groceries")) {
                seen += emissions.receive()
            }
        }
        assertTrue(
            seen.indexOf(listOf("Wallet")).let { it == -1 || it < seen.lastIndex },
            "the pre-swap archive never comes after the restored one",
        )
        collector.cancel()
    }

    @Test
    fun `the database instance stays usable after the swap`() = runBlocking {
        live.seedCategory("Wallet")
        val file = backupHolding("Groceries")

        live.replaceContentFrom(file.absolutePath)
        val writtenAfterwards = live.seedCategory("Fuel")

        assertTrue(writtenAfterwards > 0, "the same instance still writes; the pool was never closed")
        assertEquals(listOf("Fuel", "Groceries"), liveCategoryNames().sorted())
    }

    @Test
    fun `the invariants hold after the swap`() = runBlocking {
        live.seedCategory("Wallet")
        val file = backupHolding("Groceries")

        live.replaceContentFrom(file.absolutePath)

        assertEquals(
            0L,
            countInLive(
                "SELECT COUNT(*) FROM (SELECT SUM(`amount`) AS `total` FROM `entries` " +
                    "GROUP BY `transactionId`, `currency` HAVING `total` <> 0)"
            ),
            "every transaction still sums to zero per currency",
        )
        assertEquals(
            0L,
            countInLive(
                "SELECT COUNT(*) FROM `entries` `e` WHERE `e`.`dimensionId` IS NOT NULL " +
                    "AND NOT EXISTS (SELECT 1 FROM `dimensions` `d` WHERE `d`.`id` = `e`.`dimensionId`)"
            ),
            "no entry points at a dimension that is not there",
        )
        assertEquals(
            0L,
            countInLive("SELECT COUNT(*) FROM pragma_foreign_key_check"),
            "referential integrity survived an order that was derived, not written down",
        )
    }

    @Test
    fun `the file's control structures are left behind`() = runBlocking {
        live.seedCategory("Wallet")
        val file = backupHolding("Groceries")

        live.replaceContentFrom(file.absolutePath)

        assertEquals(
            0L,
            countInLive("SELECT COUNT(*) FROM `sqlite_master` WHERE `name` = 'snapshot_meta'"),
            "the file's own stamp never rides back into the live database",
        )
    }

    @Test
    fun `a table nobody wrote code for is carried across`() = runBlocking {
        val file = backupHolding("Groceries")
        // A future entity, with a foreign key, present in both databases and named
        // nowhere in the restore code. If the order is derived it is handled; if it
        // is a hand-kept list, this fails.
        val ddl = "CREATE TABLE `later_entity` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`dimensionId` INTEGER NOT NULL, " +
            "FOREIGN KEY(`dimensionId`) REFERENCES `dimensions`(`id`))"
        execOnLive(ddl)
        onFile(file) { it.execSQL(ddl) }
        onFile(file) {
            it.execSQL(
                "INSERT INTO `later_entity` (`dimensionId`) SELECT MIN(`id`) FROM `dimensions`"
            )
        }

        live.replaceContentFrom(file.absolutePath)

        assertEquals(
            1L,
            countInLive("SELECT COUNT(*) FROM `later_entity`"),
            "the row arrived, and its parent was in place before it did",
        )
        assertEquals(0L, countInLive("SELECT COUNT(*) FROM pragma_foreign_key_check"))
    }

    @Test
    fun `a connection not enforcing foreign keys is refused before anything is written`() = runBlocking {
        live.seedCategory("Wallet")
        val file = backupHolding("Groceries")
        execOnLive("PRAGMA foreign_keys = OFF")

        val failure = assertFailsWith<DatabaseRestoreException> {
            live.replaceContentFrom(file.absolutePath)
        }

        assertEquals(
            DatabaseRestoreError.FOREIGN_KEYS_DISABLED,
            failure.error,
            "the derived order is only a guarantee while the keys are enforced — without " +
                "them a wrong order commits an archive pointing at nothing, and no reading " +
                "of the result afterwards would find it",
        )
        assertEquals(
            listOf("Wallet"),
            liveCategoryNames(),
            "and it is refused before a single row is written",
        )
    }

    /**
     * A file from an installation that has been upgraded, restored onto one installed
     * fresh — the ordinary case, and the one that decides whether the copy is safe.
     *
     * The two are not the same shape. `ALTER TABLE … ADD COLUMN` appends, so a database
     * that reached v14 by migrating carries `budgets.currency` last, while one created at
     * v14 carries it sixth, where the entity declares it (`Migration11To12` and
     * `schemas/…/14.json` disagree by construction, and `MigrationSchemaEquivalenceTest`
     * already names this). A copy that matches columns by position writes each value into
     * whatever column happens to sit at that index on the other side, commits, and says
     * nothing — the period lands in the currency, the limit type lands in the period, and
     * the archive it overwrote is already gone.
     */
    @Test
    fun `a file whose columns sit in another order is copied by name`() = runBlocking {
        // The candidate: a v11 database put through the migration chain, which is how
        // every installation in the field arrived at v14.
        val migrated = File(backupFile.absolutePath + ".migrated")
        onFile(migrated) { connection ->
            V11_SCHEMA.forEach(connection::execSQL)
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `room_master_table` " +
                    "(`id` INTEGER PRIMARY KEY, `identity_hash` TEXT)"
            )
            connection.execSQL(
                "INSERT OR REPLACE INTO `room_master_table` (`id`, `identity_hash`) " +
                    "VALUES (42, 'c29a9c498d3075494afe693eb33874e0')"
            )
            connection.execSQL("PRAGMA user_version = 11")
        }
        val candidate = open(migrated.absolutePath)
        try {
            candidate.useWriterConnection { } // runs the chain, 11 → 14
        } finally {
            candidate.close()
        }
        onFile(migrated) { connection ->
            connection.execSQL(
                "INSERT INTO `budgets` (`iconCategoryId`, `iconKey`, `title`, `amount`, " +
                    "`period`, `limitType`, `percentage`, `recurringId`, `createdAt`, `currency`) " +
                    "VALUES (7, 'food', 'Mercado', 850.0, 'MONTHLY', 'PERCENTAGE', 30.0, NULL, 0, 'BRL')"
            )
        }

        live.replaceContentFrom(migrated.absolutePath)

        assertEquals(
            "BRL",
            live.useWriterConnection { connection ->
                connection.usePrepared("SELECT `currency` FROM `budgets`") { statement ->
                    statement.step()
                    statement.getText(0)
                }
            },
            "the currency of a budget is a currency, not whatever column shares its index",
        )
        assertEquals(
            "MONTHLY",
            live.useWriterConnection { connection ->
                connection.usePrepared("SELECT `period` FROM `budgets`") { statement ->
                    statement.step()
                    statement.getText(0)
                }
            },
        )
        listOf("", "-wal", "-shm").forEach { File(migrated.absolutePath + it).delete() }
    }

    @Test
    fun `a swap that fails halfway leaves the archive exactly as it was`() = runBlocking {
        live.seedCategory("Wallet")
        live.seedCategory("Rent")
        val file = backupHolding("Groceries")
        // A table the live database has and the file does not: the copy reaches it,
        // finds nothing to select from, and the whole transaction has to go back.
        execOnLive("CREATE TABLE `only_here` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")

        assertFailsWith<DatabaseRestoreException> {
            live.replaceContentFrom(file.absolutePath)
        }

        assertEquals(
            listOf("Rent", "Wallet"),
            liveCategoryNames().sorted(),
            "the archive is untouched, not half replaced",
        )
    }

    @Test
    fun `a failed swap lets go of the file it attached`() = runBlocking {
        live.seedCategory("Wallet")
        val file = backupHolding("Groceries")
        execOnLive("CREATE TABLE `only_here_too` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")

        assertFailsWith<DatabaseRestoreException> {
            live.replaceContentFrom(file.absolutePath)
        }

        // Remove what made the first attempt fail and try again. A file still
        // attached from the failed attempt would make this second `ATTACH` fail
        // under the same name — so the swap succeeding is the proof it let go.
        execOnLive("DROP TABLE `only_here_too`")
        live.replaceContentFrom(file.absolutePath)

        assertEquals(listOf("Groceries"), liveCategoryNames())
    }
}
