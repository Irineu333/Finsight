package com.neoutils.finsight.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.neoutils.finsight.database.entity.CategoryEntity
import com.neoutils.finsight.database.exception.DatabaseCaptureError
import com.neoutils.finsight.database.exception.DatabaseCaptureException
import com.neoutils.finsight.database.snapshot.captureInto
import com.neoutils.finsight.domain.model.DimensionKind
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * The capture, over a real database on a real file — never `inMemoryDatabaseBuilder`,
 * whose single-connection pool would not exercise the 1-writer/4-readers pool WAL
 * actually gives the app.
 *
 * What the capture owes is stated as three separate promises, because they fail
 * separately: the file stands alone, it carries the identity that lets this app
 * recognise it as its own, and it shows nothing that was not committed when it was
 * taken.
 */
class DatabaseCaptureTest {

    private val liveFile: File = File.createTempFile("finsight-capture-live", ".db").also { it.delete() }
    private val capturedFile: File = File.createTempFile("finsight-capture-out", ".db").also { it.delete() }

    private val live = getRoomDatabase(
        builder = getDatabaseBuilder(path = liveFile.absolutePath),
        baseCurrency = "BRL",
        currencySeeding = testSeeding(),
    )

    @AfterTest
    fun tearDown() {
        live.close()
        listOf(liveFile, capturedFile).forEach { file ->
            listOf("", "-wal", "-shm").forEach { File(file.absolutePath + it).delete() }
        }
    }

    private suspend fun AppDatabase.seed(name: String) {
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

    private fun <T> onFile(file: File, block: (SQLiteConnection) -> T): T {
        val connection = BundledSQLiteDriver().open(file.absolutePath)
        return try {
            block(connection)
        } finally {
            connection.close()
        }
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

    @Test
    fun `the captured file stands alone`() = runBlocking {
        live.seed("Groceries")

        live.captureInto(capturedFile.absolutePath)

        assertTrue(capturedFile.exists(), "the capture wrote the file")
        listOf("-wal", "-shm").forEach { suffix ->
            assertFalse(
                File(capturedFile.absolutePath + suffix).exists(),
                "the captured file carries no $suffix alongside it",
            )
        }
        assertEquals(
            "SQLite format 3",
            capturedFile.readBytes().copyOfRange(0, 15).decodeToString(),
            "the magic bytes identify a SQLite database",
        )
        assertEquals(
            "Groceries",
            onFile(capturedFile) { it.scalarText("SELECT `name` FROM `categories`") },
            "the content travelled with the file",
        )
    }

    @Test
    fun `the captured file is recognisable as this app's own`() = runBlocking {
        live.seed("Groceries")

        live.captureInto(capturedFile.absolutePath)

        val liveIdentity = live.useWriterConnection { connection ->
            connection.usePrepared("SELECT `identity_hash` FROM `room_master_table`") { statement ->
                statement.step()
                statement.getText(0)
            }
        }

        onFile(capturedFile) { captured ->
            assertEquals(
                14L,
                captured.scalarLong("PRAGMA user_version"),
                "the schema version travelled",
            )
            assertEquals(
                liveIdentity,
                captured.scalarText("SELECT `identity_hash` FROM `room_master_table`"),
                "Room recognises the file as a database of this very schema",
            )
            assertTrue(
                captured.scalarLong("SELECT COUNT(*) FROM `sqlite_sequence`") > 0,
                "the AUTOINCREMENT counters travelled too",
            )
        }
    }

    @Test
    fun `an uncommitted write in another connection is left out`() = runBlocking {
        live.seed("Groceries")

        val other = BundledSQLiteDriver().open(liveFile.absolutePath)
        try {
            other.execSQL("BEGIN IMMEDIATE")
            other.execSQL("INSERT INTO `dimensions` (`kind`) VALUES ('CATEGORY')")

            live.captureInto(capturedFile.absolutePath)

            assertEquals(
                1L,
                onFile(capturedFile) { it.scalarLong("SELECT COUNT(*) FROM `dimensions`") },
                "only what was committed at capture time is in the file",
            )
        } finally {
            other.execSQL("ROLLBACK")
            other.close()
        }
    }

    @Test
    fun `a refusal this database has no name for still arrives typed`() = runBlocking {
        live.seed("Groceries")

        val failure = assertFailsWith<DatabaseCaptureException> {
            withTimeout(5.seconds) {
                live.useWriterConnection { connection ->
                    connection.immediateTransaction {
                        live.captureInto(capturedFile.absolutePath)
                    }
                }
            }
        }

        assertEquals(
            DatabaseCaptureError.UNKNOWN,
            failure.error,
            "SQLite refuses a capture from inside a transaction, and that refusal has no " +
                "constant of its own — what matters is that it does not escape untyped",
        )
        assertFalse(capturedFile.exists(), "nothing was written")
    }

    @Test
    fun `capturing onto a path that already exists is refused`() = runBlocking {
        live.seed("Groceries")
        capturedFile.writeBytes(byteArrayOf(1, 2, 3))

        val failure = assertFailsWith<DatabaseCaptureException> {
            live.captureInto(capturedFile.absolutePath)
        }

        assertEquals(DatabaseCaptureError.DESTINATION_EXISTS, failure.error)
        assertEquals(
            3,
            capturedFile.readBytes().size,
            "the file that was already there is untouched",
        )
    }
}
