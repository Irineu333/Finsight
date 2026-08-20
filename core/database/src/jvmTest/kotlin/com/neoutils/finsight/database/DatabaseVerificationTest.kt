package com.neoutils.finsight.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.neoutils.finsight.database.entity.CategoryEntity
import com.neoutils.finsight.database.snapshot.CandidateRejection
import com.neoutils.finsight.database.snapshot.CandidateVerification
import com.neoutils.finsight.database.snapshot.CandidateVerifier
import com.neoutils.finsight.database.snapshot.captureInto
import com.neoutils.finsight.domain.model.DimensionKind
import java.io.File
import java.io.RandomAccessFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * The gate a file has to pass before it is allowed to replace the user's archive.
 *
 * The refusals are stated one per cause, because the screen says something different
 * for each — "this app is out of date" is not "this file is broken". Four of them
 * share a shape worth naming: a file that is a perfectly healthy SQLite database and
 * simply is not ours. Left to integrity alone, every one of them is approved, because
 * Room meets `user_version = 0`, creates the schema, seeds the currencies and hands
 * back something indistinguishable from an empty backup. The restore then succeeds
 * and takes the archive with it.
 *
 * The acceptance of a genuinely empty archive is asserted beside them, so the gate
 * cannot drift into refusing by size rather than by identity.
 */
class DatabaseVerificationTest {

    private val liveFile: File = File.createTempFile("finsight-verify-live", ".db").also { it.delete() }
    private val candidates = mutableListOf<File>()

    private fun open(path: String) = getRoomDatabase(
        builder = getDatabaseBuilder(path = path),
        baseCurrency = "BRL",
        currencySeeding = testSeeding(),
    )

    private val live = open(liveFile.absolutePath)

    private val verifier = CandidateVerifier(::open)

    @AfterTest
    fun tearDown() {
        live.close()
        (candidates + liveFile).forEach { file ->
            listOf("", "-wal", "-shm").forEach { File(file.absolutePath + it).delete() }
        }
    }

    private fun candidate(name: String): File =
        File.createTempFile("finsight-verify-$name", ".db")
            .also { it.delete(); candidates += it }

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

    /** A file this app itself produced, which every refusal below is a departure from. */
    private suspend fun goodBackup(name: String = "good"): File = candidate(name).also {
        live.captureInto(it.absolutePath, appVersion = "1.2.3", platform = "jvm")
    }

    private fun onFile(file: File, block: (SQLiteConnection) -> Unit) {
        val connection = BundledSQLiteDriver().open(file.absolutePath)
        try {
            connection.execSQL("PRAGMA foreign_keys = OFF")
            block(connection)
        } finally {
            connection.close()
        }
    }

    private suspend fun rejectionOf(file: File): CandidateRejection {
        val verification = verifier.verify(file.absolutePath)
        assertIs<CandidateVerification.Rejected>(
            verification,
            "expected ${file.name} to be refused, and it was accepted",
        )
        return verification.reason
    }

    private suspend fun acceptanceOf(file: File): CandidateVerification.Accepted {
        val verification = verifier.verify(file.absolutePath)
        assertIs<CandidateVerification.Accepted>(
            verification,
            "expected ${file.name} to be accepted, and it was refused",
        )
        return verification
    }

    // -------------------------------------------------------------- layers 1 to 3

    @Test
    fun `a file that is not a database is refused`() = runBlocking {
        val file = candidate("junk").also { it.writeBytes(ByteArray(4096) { 0x7A }) }

        assertEquals(CandidateRejection.NOT_A_DATABASE, rejectionOf(file))
    }

    @Test
    fun `a file carrying the magic bytes and nothing else is refused`() = runBlocking {
        val file = candidate("magiconly").also {
            it.writeBytes("SQLite format 3 ".encodeToByteArray() + ByteArray(4080) { 0x7A })
        }

        assertEquals(
            CandidateRejection.NOT_A_DATABASE,
            rejectionOf(file),
            "sixteen right bytes are not evidence of a database",
        )
    }

    @Test
    fun `a corrupted database is refused`() = runBlocking {
        live.seedCategory("Groceries")
        val file = goodBackup("corrupt")
        RandomAccessFile(file, "rw").use { handle ->
            handle.seek(4096)
            handle.write(ByteArray(8192) { 0x5A })
        }

        assertEquals(CandidateRejection.CORRUPTED, rejectionOf(file))
    }

    @Test
    fun `a schema version newer than this app is refused with its own cause`() = runBlocking {
        val file = goodBackup("newer")
        onFile(file) { it.execSQL("PRAGMA user_version = 15") }

        assertEquals(
            CandidateRejection.SCHEMA_TOO_NEW,
            rejectionOf(file),
            "the screen asks the user to update the app, so this cannot look like a broken file",
        )
    }

    // ------------------------------------------------- the four that are not ours

    @Test
    fun `a zero-byte file is refused`() = runBlocking {
        val file = candidate("zero").also { it.createNewFile() }

        assertEquals(CandidateRejection.NOT_FROM_THIS_APP, rejectionOf(file))
    }

    @Test
    fun `a valid database with no tables is refused`() = runBlocking {
        val file = candidate("emptyvalid")
        onFile(file) {
            it.execSQL("CREATE TABLE t(a)")
            it.execSQL("DROP TABLE t")
        }

        assertEquals(
            CandidateRejection.NOT_FROM_THIS_APP,
            rejectionOf(file),
            "it is a healthy SQLite database, and it is not one of ours",
        )
    }

    @Test
    fun `a database from another app is refused`() = runBlocking {
        val file = candidate("foreign")
        onFile(file) {
            it.execSQL("CREATE TABLE cookies(host TEXT, value TEXT)")
            it.execSQL("INSERT INTO cookies VALUES('example.com', 'x')")
        }

        assertEquals(CandidateRejection.NOT_FROM_THIS_APP, rejectionOf(file))
    }

    @Test
    fun `the main file copied without its journal is refused`() = runBlocking {
        live.seedCategory("Groceries")
        val orphan = candidate("walorphan")
        // Copied while the live database is still open, so what it holds sits in the
        // `-wal` left behind. This is the file D2 describes.
        orphan.writeBytes(liveFile.readBytes())

        assertEquals(
            CandidateRejection.NOT_FROM_THIS_APP,
            rejectionOf(orphan),
            "the schema never made it out of the write-ahead log",
        )
    }

    // -------------------------------------------------------------- layers 4 and 5

    @Test
    fun `a database whose schema identity differs is refused`() = runBlocking {
        val file = goodBackup("identity")
        onFile(file) {
            it.execSQL("UPDATE `room_master_table` SET `identity_hash` = 'not-the-hash-of-this-app'")
        }

        assertEquals(CandidateRejection.SCHEMA_MISMATCH, rejectionOf(file))
    }

    @Test
    fun `an unbalanced ledger is refused`() = runBlocking {
        val file = goodBackup("unbalanced")
        onFile(file) {
            it.execSQL(
                "INSERT INTO `accounts` (`name`, `type`, `currency`, `iconKey`, `isDefault`, " +
                    "`createdAt`, `isArchived`, `yieldsInterest`) " +
                    "VALUES ('Wallet', 'ASSET', 'BRL', 'wallet', 0, 0, 0, 0)"
            )
            it.execSQL("INSERT INTO `transactions` (`title`, `date`) VALUES ('t', '2026-01-01')")
            it.execSQL(
                "INSERT INTO `entries` (`transactionId`, `accountId`, `amount`, `currency`) " +
                    "SELECT (SELECT MAX(`id`) FROM `transactions`), " +
                    "(SELECT MAX(`id`) FROM `accounts`), 1000, 'BRL'"
            )
        }

        assertEquals(CandidateRejection.UNBALANCED_LEDGER, rejectionOf(file))
    }

    @Test
    fun `an entry pointing at a dimension that does not exist is refused`() = runBlocking {
        val file = goodBackup("orphan")
        onFile(file) {
            it.execSQL(
                "INSERT INTO `accounts` (`name`, `type`, `currency`, `iconKey`, `isDefault`, " +
                    "`createdAt`, `isArchived`, `yieldsInterest`) " +
                    "VALUES ('Wallet', 'ASSET', 'BRL', 'wallet', 0, 0, 0, 0)"
            )
            it.execSQL("INSERT INTO `transactions` (`title`, `date`) VALUES ('t', '2026-01-01')")
            it.execSQL(
                "INSERT INTO `entries` (`transactionId`, `accountId`, `amount`, `currency`, " +
                    "`dimensionId`) " +
                    "SELECT (SELECT MAX(`id`) FROM `transactions`), " +
                    "(SELECT MAX(`id`) FROM `accounts`), 1000, 'BRL', 999999"
            )
            it.execSQL(
                "INSERT INTO `entries` (`transactionId`, `accountId`, `amount`, `currency`) " +
                    "SELECT (SELECT MAX(`id`) FROM `transactions`), " +
                    "(SELECT MAX(`id`) FROM `accounts`), -1000, 'BRL'"
            )
        }

        assertEquals(CandidateRejection.ORPHAN_DIMENSION, rejectionOf(file))
    }

    @Test
    fun `a foreign key violation is refused`() = runBlocking {
        val file = goodBackup("fk")
        onFile(file) {
            it.execSQL(
                "INSERT INTO `categories` (`name`, `iconKey`, `type`, `createdAt`, " +
                    "`dimensionId`, `isArchived`) " +
                    "VALUES ('Nowhere', 'shopping', 'EXPENSE', 0, 999999, 0)"
            )
        }

        assertEquals(CandidateRejection.FOREIGN_KEY_VIOLATION, rejectionOf(file))
    }

    // ------------------------------------------------------------ what is accepted

    @Test
    fun `a backup this app produced is accepted, and says where it came from`() = runBlocking {
        live.seedCategory("Groceries")
        val file = goodBackup()

        val accepted = acceptanceOf(file)

        val origin = assertNotNull(accepted.origin, "the file carries its stamp")
        assertEquals("1.2.3", origin.appVersion)
        assertEquals("jvm", origin.platform)
        assertEquals(1L, origin.formatVersion)
        assertTrue(origin.createdAt > 0)
        assertEquals(1L, accepted.counts.categories, "the counts describe the archive in the file")
        assertEquals(0L, accepted.counts.transactions)
    }

    @Test
    fun `a backup of an empty archive is accepted`() = runBlocking {
        val file = goodBackup("empty")

        val accepted = acceptanceOf(file)

        assertEquals(0L, accepted.counts.accounts, "zero accounts is what a new install holds")
        assertEquals(0L, accepted.counts.transactions)
        assertEquals(0L, accepted.counts.categories)
    }

    @Test
    fun `a backup with no stamp is accepted, with its origin unknown`() = runBlocking {
        live.seedCategory("Groceries")
        val file = goodBackup("nostamp")
        onFile(file) { it.execSQL("DROP TABLE `snapshot_meta`") }

        val accepted = acceptanceOf(file)

        assertNull(
            accepted.origin,
            "an older file still restores; the screen is what says the origin is unknown",
        )
        assertEquals(1L, accepted.counts.categories)
    }

    // ---------------------------------------------------------------- side effects

    @Test
    fun `verifying a path that holds nothing creates nothing`() = runBlocking {
        val missing = candidate("missing")

        assertEquals(CandidateRejection.NOT_A_DATABASE, rejectionOf(missing))
        assertFalse(
            missing.exists(),
            "with the default flags SQLite creates the file, and the file it creates passes " +
                "every check that reads bytes",
        )
    }

    @Test
    fun `a refused candidate leaves the live database untouched`() = runBlocking {
        live.seedCategory("Groceries")
        val junk = candidate("junk2").also { it.writeBytes(ByteArray(4096) { 0x7A }) }

        rejectionOf(junk)

        assertEquals(
            listOf("Groceries"),
            live.categoryDao().observeAllCategories().first().map { it.name },
            "the archive in use is exactly what it was",
        )
    }
}
