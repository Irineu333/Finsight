package com.neoutils.finsight.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.neoutils.finsight.database.migration.Migration12To13
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * The copy taken before a migration, over real files — the one capture in the app that
 * nobody asks for and that has exactly one chance to happen.
 *
 * What is asserted is the *order*, not the existence of a file: the copy declares the
 * version the archive was on and the archive declares the one it was migrated to. A test
 * that only checked the file was written would pass just as happily on a copy taken
 * afterwards, which is the single thing this feature exists to prevent.
 *
 * The archive at the old version is built the way every migration test builds one — the
 * frozen v12 DDL plus the real `12 → 13` — because a hand-written fixture at v13 would
 * only prove itself.
 */
class PreMigrationCaptureTest {

    private val liveFile: File = File.createTempFile("finsight-premigration-live", ".db").also { it.delete() }
    private val copyFile: File = File.createTempFile("finsight-premigration-copy", ".db").also { it.delete() }

    @AfterTest
    fun tearDown() {
        listOf(liveFile, copyFile).forEach { file ->
            listOf("", "-wal", "-shm").forEach { File(file.absolutePath + it).delete() }
        }
    }

    /** A device on the version before this build's, with something of the user's in it. */
    private fun writePreviousVersion() {
        BundledSQLiteDriver().open(liveFile.absolutePath).use { connection ->
            V12_SCHEMA.forEach(connection::execSQL)
            Migration12To13(baseCurrency = "BRL").migrate(connection)
            connection.execSQL("INSERT INTO `dimensions` (`kind`) VALUES ('CATEGORY')")
            connection.execSQL(
                "INSERT INTO `categories` " +
                    "(`name`, `iconKey`, `type`, `createdAt`, `dimensionId`, `isArchived`) " +
                    "VALUES ('Groceries', 'shopping', 'EXPENSE', 0, 1, 0)"
            )
            connection.execSQL("PRAGMA user_version = 13")
        }
    }

    private fun open(captureInto: String? = null) = getRoomDatabase(
        builder = getDatabaseBuilder(path = liveFile.absolutePath, captureInto = captureInto),
        baseCurrency = "BRL",
        currencySeeding = testSeeding(),
    )

    /**
     * Room's `build()` opens nothing: the chain runs on the first access, and only a query
     * makes the migration this test is about actually happen.
     */
    private suspend fun AppDatabase.openAndClose() {
        try {
            accountDao().getAllLedgerAccounts()
        } finally {
            close()
        }
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
    fun `a destination and a pending migration produce a copy of what was there`() = runBlocking {
        writePreviousVersion()

        open(captureInto = copyFile.absolutePath).openAndClose()

        assertTrue(copyFile.exists(), "the copy was written")
        assertEquals(
            13L,
            onFile(copyFile) { it.scalarLong("PRAGMA user_version") },
            "the copy is of the archive as it was, before the migration touched it",
        )
        assertEquals(
            14L,
            onFile(liveFile) { it.scalarLong("PRAGMA user_version") },
            "and the archive itself did migrate",
        )
        assertEquals(
            "Groceries",
            onFile(copyFile) { it.scalarText("SELECT `name` FROM `categories`") },
            "what the user had travelled with the copy",
        )
        listOf("-wal", "-shm").forEach { suffix ->
            assertFalse(
                File(copyFile.absolutePath + suffix).exists(),
                "the copy opens on its own, with no $suffix beside it",
            )
        }
    }

    @Test
    fun `no destination means no copy, and the migration runs regardless`() = runBlocking {
        writePreviousVersion()

        open().openAndClose()

        assertFalse(copyFile.exists(), "nothing was written where a destination was not given")
        assertEquals(
            14L,
            onFile(liveFile) { it.scalarLong("PRAGMA user_version") },
            "the migration is not conditional on any of this",
        )
    }

    @Test
    fun `an archive already on this version has nothing to protect`() = runBlocking {
        writePreviousVersion()
        open().openAndClose()

        open(captureInto = copyFile.absolutePath).openAndClose()

        assertFalse(
            copyFile.exists(),
            "the second opening had no migration to run, so there was nothing to copy",
        )
    }

    @Test
    fun `a fresh install creates nothing to copy from`() = runBlocking {
        getDatabaseBuilder(path = liveFile.absolutePath, captureInto = copyFile.absolutePath)

        assertFalse(
            liveFile.exists(),
            "asking a file that is not there for its version must not bring it into being",
        )
        assertFalse(copyFile.exists(), "and there is nothing to write a copy of")

        open(captureInto = copyFile.absolutePath).openAndClose()

        assertTrue(liveFile.exists(), "the app still opens, on a database it just created")
        assertFalse(copyFile.exists(), "a database being created is not a database being migrated")
    }

    @Test
    fun `what only the write-ahead log holds is copied too`() = runBlocking {
        // The archive an app that did not close cleanly leaves behind: the committed rows
        // are in the log, not yet in the file. The copy is taken read-only, and this is
        // what says the flag does not cost it the log.
        val session = BundledSQLiteDriver().open(liveFile.absolutePath)
        try {
            session.execSQL("PRAGMA journal_mode = WAL")
            V12_SCHEMA.forEach(session::execSQL)
            Migration12To13(baseCurrency = "BRL").migrate(session)
            session.execSQL("INSERT INTO `dimensions` (`kind`) VALUES ('CATEGORY')")
            session.execSQL(
                "INSERT INTO `categories` " +
                    "(`name`, `iconKey`, `type`, `createdAt`, `dimensionId`, `isArchived`) " +
                    "VALUES ('Groceries', 'shopping', 'EXPENSE', 0, 1, 0)"
            )
            session.execSQL("PRAGMA user_version = 13")
            assertTrue(
                File(liveFile.absolutePath + "-wal").exists(),
                "the fixture is only worth anything with a log actually beside the file",
            )

            getDatabaseBuilder(path = liveFile.absolutePath, captureInto = copyFile.absolutePath)
        } finally {
            session.close()
        }

        assertEquals(
            13L,
            onFile(copyFile) { it.scalarLong("PRAGMA user_version") },
            "the version the log carries, not the one the file was left at",
        )
        assertEquals(
            "Groceries",
            onFile(copyFile) { it.scalarText("SELECT `name` FROM `categories`") },
            "and the rows the log carries with it",
        )
    }

    @Test
    fun `a capture that fails leaves the migration to happen anyway`() = runBlocking {
        writePreviousVersion()
        // `VACUUM INTO` refuses a destination that already holds bytes — the same refusal
        // a full disk produces, and the only one a test can stage.
        copyFile.writeBytes(byteArrayOf(1, 2, 3))

        open(captureInto = copyFile.absolutePath).openAndClose()

        assertEquals(
            14L,
            onFile(liveFile) { it.scalarLong("PRAGMA user_version") },
            "the app opened and the migration ran, with no copy behind it",
        )
        assertEquals(
            3,
            copyFile.readBytes().size,
            "and the refused capture wrote nothing over what was already there",
        )
    }
}
