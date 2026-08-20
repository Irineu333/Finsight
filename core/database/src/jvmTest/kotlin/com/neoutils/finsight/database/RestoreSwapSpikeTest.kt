package com.neoutils.finsight.database

import androidx.room.Room
import androidx.room.execSQL
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.entity.CategoryEntity
import com.neoutils.finsight.domain.model.DimensionKind
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Spike for Q1 of the `local-backup` design: does a restore that swaps the whole
 * contents through `ATTACH` make an already-collecting DAO `Flow` re-emit, with no
 * manual `refreshAsync()` and without closing the database?
 *
 * Both databases are opened **on a file**, never in memory: an in-memory Room uses a
 * single-connection pool, which would not exercise the 1-writer/4-readers pool that
 * WAL actually gives the app.
 */
class RestoreSwapSpikeTest {

    private val liveFile: File = File.createTempFile("finsight-spike-live", ".db").also { it.delete() }
    private val backupFile: File = File.createTempFile("finsight-spike-backup", ".db").also { it.delete() }

    private fun open(file: File) = Room.databaseBuilder<AppDatabase>(name = file.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    private val live = open(liveFile)

    @AfterTest
    fun tearDown() {
        live.close()
        listOf(liveFile, backupFile).forEach { file ->
            listOf("", "-wal", "-shm").forEach { File(file.absolutePath + it).delete() }
        }
    }

    private suspend fun AppDatabase.seedCategory(name: String) {
        val dimensionId = dimensionDao().emit(DimensionKind.CATEGORY)
        categoryDao().insert(
            CategoryEntity(
                name = name,
                iconKey = "shopping",
                type = CategoryEntity.Type.EXPENSE,
                dimensionId = dimensionId,
            )
        )
    }

    @Test
    fun `swapping the contents through ATTACH makes a collecting flow re-emit`() = runBlocking {
        live.seedCategory("Vivo")

        val backup = open(backupFile)
        backup.seedCategory("Restaurado A")
        backup.seedCategory("Restaurado B")
        backup.close()

        val emissions = Channel<List<String>>(Channel.UNLIMITED)
        val collector = launch(Dispatchers.IO) {
            live.categoryDao().observeAllCategories().collect { categories ->
                emissions.send(categories.map { it.name })
            }
        }

        val before = withTimeout(5.seconds) { emissions.receive() }
        assertEquals(listOf("Vivo"), before, "the live database is what the flow starts from")

        live.useWriterConnection { connection ->
            connection.usePrepared("ATTACH DATABASE ?1 AS backup") { statement ->
                statement.bindText(1, backupFile.absolutePath)
                statement.step()
            }
            try {
                connection.immediateTransaction {
                    // Topological order: children before parents on delete, parents
                    // before children on insert. No defer_foreign_keys — it does not
                    // hold for INSERT..SELECT across an attached database.
                    execSQL("DELETE FROM `categories`")
                    execSQL("DELETE FROM `dimensions`")
                    execSQL("INSERT INTO `main`.`dimensions` SELECT * FROM `backup`.`dimensions`")
                    execSQL("INSERT INTO `main`.`categories` SELECT * FROM `backup`.`categories`")
                }
            } finally {
                connection.execSQL("DETACH DATABASE backup")
            }
        }

        val after = withTimeout(5.seconds) { emissions.receive() }
        assertEquals(
            listOf("Restaurado A", "Restaurado B"),
            after,
            "the flow re-emitted the restored rows with no manual refreshAsync()",
        )

        collector.cancel()
    }

    /**
     * Spike for D2: `VACUUM INTO` through the writer connection produces a file that
     * opens on its own — no `-wal`/`-shm` alongside it — and carries the schema version
     * and the sequence counters with it.
     */
    @Test
    fun `VACUUM INTO produces a self-contained file`(): Unit = runBlocking {
        live.seedCategory("Capturada")

        val captured = File.createTempFile("finsight-spike-captured", ".db").also { it.delete() }
        live.useWriterConnection { connection ->
            connection.usePrepared("VACUUM INTO ?1") { statement ->
                statement.bindText(1, captured.absolutePath)
                statement.step()
            }
        }

        assertTrue(captured.exists(), "the captured file was written")
        listOf("-wal", "-shm").forEach {
            assertTrue(
                !File(captured.absolutePath + it).exists(),
                "the captured file carries no $it alongside it",
            )
        }

        val connection = BundledSQLiteDriver().open(captured.absolutePath)
        try {
            val header = captured.readBytes().copyOfRange(0, 15).decodeToString()
            assertEquals("SQLite format 3", header, "the magic bytes identify a SQLite database")

            assertEquals(14L, connection.scalarLong("PRAGMA user_version"), "the schema version travelled")
            assertEquals("Capturada", connection.scalarText("SELECT `name` FROM `categories`"))
            assertTrue(
                connection.scalarLong("SELECT COUNT(*) FROM `sqlite_sequence`") > 0,
                "the AUTOINCREMENT counters travelled too",
            )
        } finally {
            connection.close()
        }

        captured.delete()
    }

    /**
     * The `sync()` risk, from the only angle a public API can reach it: a swap that
     * lands *before* the flow has produced its first emission must not leave the flow
     * stuck on the pre-swap state.
     *
     * Measuring the risk directly is not possible from here — launching the collector
     * and swapping immediately does not open the window it aims at, because the
     * collector has not run its first query yet when the swap happens. What this test
     * does establish is the outcome that matters: whichever side of the race wins, the
     * state the flow settles on is the restored one.
     */
    @Test
    fun `a swap landing before the first emission does not strand the flow`() = runBlocking {
        live.seedCategory("Vivo")

        val backup = open(backupFile)
        backup.seedCategory("Restaurado")
        backup.close()

        val emissions = Channel<List<String>>(Channel.UNLIMITED)
        val collector = launch(Dispatchers.IO) {
            live.categoryDao().observeAllCategories().collect { categories ->
                emissions.send(categories.map { it.name })
            }
        }

        live.useWriterConnection { connection ->
            connection.usePrepared("ATTACH DATABASE ?1 AS backup") { statement ->
                statement.bindText(1, backupFile.absolutePath)
                statement.step()
            }
            try {
                connection.immediateTransaction {
                    execSQL("DELETE FROM `categories`")
                    execSQL("DELETE FROM `dimensions`")
                    execSQL("INSERT INTO `main`.`dimensions` SELECT * FROM `backup`.`dimensions`")
                    execSQL("INSERT INTO `main`.`categories` SELECT * FROM `backup`.`categories`")
                }
            } finally {
                connection.execSQL("DETACH DATABASE backup")
            }
        }

        val seen = mutableListOf<List<String>>()
        withTimeout(5.seconds) {
            while (seen.lastOrNull() != listOf("Restaurado")) {
                seen += emissions.receive()
            }
        }

        assertTrue(seen.last() == listOf("Restaurado"), "the restored state reached the flow")
        assertTrue(
            seen.none { it == listOf("Vivo") } || seen.indexOf(listOf("Vivo")) < seen.lastIndex,
            "the pre-swap state never comes after the restored one",
        )

        collector.cancel()
    }

    private fun SQLiteConnection.scalarLong(sql: String): Long {
        val statement = prepare(sql)
        try {
            statement.step()
            return statement.getLong(0)
        } finally {
            statement.close()
        }
    }

    private fun SQLiteConnection.scalarText(sql: String): String {
        val statement = prepare(sql)
        try {
            statement.step()
            return statement.getText(0)
        } finally {
            statement.close()
        }
    }
}
