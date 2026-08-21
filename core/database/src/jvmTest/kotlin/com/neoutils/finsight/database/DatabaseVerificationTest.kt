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

    /**
     * From `schemas/…/11.json`. Frozen history: a real backup of that vintage carries this
     * in its `room_master_table`, and a fixture that carries something else is not one.
     */
    private val V11_IDENTITY_HASH = "c29a9c498d3075494afe693eb33874e0"

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

    /**
     * A file of the vintage the migration chain still has to carry, holding whatever
     * [seed] puts in it.
     *
     * The identity hash is the one `schemas/…/11.json` froze, because layer 2 reads it
     * before any migration runs: a fixture carrying anything else is refused for its
     * identity and never reaches the chain this is here to exercise.
     */
    private fun v11Candidate(name: String, seed: (SQLiteConnection) -> Unit = {}): File =
        candidate(name).also { file ->
            onFile(file) { connection ->
                V11_SCHEMA.forEach(connection::execSQL)
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `room_master_table` " +
                        "(`id` INTEGER PRIMARY KEY, `identity_hash` TEXT)"
                )
                connection.execSQL(
                    "INSERT OR REPLACE INTO `room_master_table` (`id`, `identity_hash`) " +
                        "VALUES (42, '$V11_IDENTITY_HASH')"
                )
                seed(connection)
                connection.execSQL("PRAGMA user_version = 11")
            }
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

    /**
     * A migration guard speaks about the content of the file, and the file is refused for
     * what the guard found rather than for its schema. The two are not interchangeable:
     * this file's schema is exactly the one this app expects for its version — it is what
     * the schema holds that no build of this app would have written.
     */
    @Test
    fun `an unbalanced ledger a migration finds is refused for the ledger`() = runBlocking {
        val file = v11Candidate("v11unbalanced") { connection ->
            connection.execSQL(
                "INSERT INTO `accounts` (`name`, `type`, `currency`, `iconKey`, `isDefault`, " +
                    "`createdAt`, `isArchived`, `yieldsInterest`) " +
                    "VALUES ('Wallet', 'ASSET', 'BRL', 'wallet', 0, 0, 0, 0)"
            )
            connection.execSQL(
                "INSERT INTO `transactions` (`title`, `date`) VALUES ('t', '2026-01-01')"
            )
            connection.execSQL(
                "INSERT INTO `entries` (`transactionId`, `accountId`, `amount`, `currency`) " +
                    "SELECT (SELECT MAX(`id`) FROM `transactions`), " +
                    "(SELECT MAX(`id`) FROM `accounts`), 1000, 'BRL'"
            )
        }

        assertEquals(
            CandidateRejection.UNBALANCED_LEDGER,
            rejectionOf(file),
            "the migration's own guard named the finding, and the name survives the layer",
        )
    }

    @Test
    fun `a migration that aborts over the file's content is refused as that`() = runBlocking {
        val file = v11Candidate("v11orphan") { connection ->
            connection.execSQL(
                "INSERT INTO `accounts` (`name`, `type`, `currency`, `iconKey`, `isDefault`, " +
                    "`createdAt`, `isArchived`, `yieldsInterest`) " +
                    "VALUES ('Wallet', 'ASSET', 'BRL', 'wallet', 0, 0, 0, 0)"
            )
            connection.execSQL(
                "INSERT INTO `transactions` (`title`, `date`) VALUES ('t', '2026-01-01')"
            )
            // Balanced, so the guard before this one has nothing to say, and pointing at
            // a dimension the file does not hold.
            connection.execSQL(
                "INSERT INTO `entries` (`transactionId`, `accountId`, `amount`, `currency`, " +
                    "`dimensionId`) " +
                    "SELECT (SELECT MAX(`id`) FROM `transactions`), " +
                    "(SELECT MAX(`id`) FROM `accounts`), 1000, 'BRL', 999999"
            )
            connection.execSQL(
                "INSERT INTO `entries` (`transactionId`, `accountId`, `amount`, `currency`) " +
                    "SELECT (SELECT MAX(`id`) FROM `transactions`), " +
                    "(SELECT MAX(`id`) FROM `accounts`), -1000, 'BRL'"
            )
        }

        assertEquals(
            CandidateRejection.MIGRATION_ABORTED,
            rejectionOf(file),
            "a migration refusing what it was handed is not the file declaring a schema " +
                "this app does not know",
        )
    }

    /**
     * Layer 4 proves less than its name suggests, and this is the file that finds the gap.
     *
     * When a candidate already declares this build's schema version, Room compares the
     * identity hash and validates nothing else, so the tables layer 5 reads are tables
     * nobody has looked at. A file carrying the hash without them reaches raw SQL with
     * nothing to run it against — and that is a refusal, because the file said what it was
     * and is not it. It used to be neither: the exception left `verify()` altogether and
     * rose through the scope of whoever called it.
     */
    @Test
    fun `a file carrying this app's identity without its tables is refused`() = runBlocking {
        live.seedCategory("Groceries")
        val file = goodBackup("mutilated")
        onFile(file) { it.execSQL("DROP TABLE `entries`") }

        assertEquals(
            CandidateRejection.SCHEMA_MISMATCH,
            rejectionOf(file),
            "a hash is a claim about the schema, and this file does not hold what it claims",
        )
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

    @Test
    fun `a dimension landing on an account type it may not land on is refused`() = runBlocking {
        val file = goodBackup("landing")
        onFile(file) {
            it.execSQL(
                "INSERT INTO `accounts` (`name`, `type`, `currency`, `iconKey`, `isDefault`, " +
                    "`createdAt`, `isArchived`, `yieldsInterest`) " +
                    "VALUES ('Wallet', 'ASSET', 'BRL', 'wallet', 0, 0, 0, 0)"
            )
            it.execSQL(
                "INSERT INTO `accounts` (`name`, `type`, `currency`, `iconKey`, `isDefault`, " +
                    "`createdAt`, `isArchived`, `yieldsInterest`) " +
                    "VALUES ('Groceries', 'EXPENSE', 'BRL', 'shopping', 0, 0, 0, 0)"
            )
            it.execSQL("INSERT INTO `dimensions` (`kind`) VALUES ('INVOICE')")
            it.execSQL("INSERT INTO `transactions` (`title`, `date`) VALUES ('t', '2026-01-01')")
            // The invoice dimension lands on the nominal leg, where only a category may.
            it.execSQL(
                "INSERT INTO `entries` (`transactionId`, `accountId`, `amount`, `currency`, " +
                    "`dimensionId`) " +
                    "SELECT (SELECT MAX(`id`) FROM `transactions`), " +
                    "(SELECT `id` FROM `accounts` WHERE `name` = 'Groceries'), 1000, 'BRL', " +
                    "(SELECT MAX(`id`) FROM `dimensions`)"
            )
            it.execSQL(
                "INSERT INTO `entries` (`transactionId`, `accountId`, `amount`, `currency`) " +
                    "SELECT (SELECT MAX(`id`) FROM `transactions`), " +
                    "(SELECT `id` FROM `accounts` WHERE `name` = 'Wallet'), -1000, 'BRL'"
            )
        }

        assertEquals(
            CandidateRejection.MISPLACED_DIMENSION,
            rejectionOf(file),
            "it sums to zero, the dimension exists and no key is broken — the landing is all " +
                "that is wrong, and every sum by that dimension would be quietly wrong after it",
        )
    }

    // ------------------------------------------------------------ what is accepted

    @Test
    fun `a dimension landing where it belongs is accepted`() = runBlocking<Unit> {
        val file = goodBackup("landingok")
        onFile(file) {
            it.execSQL(
                "INSERT INTO `accounts` (`name`, `type`, `currency`, `iconKey`, `isDefault`, " +
                    "`createdAt`, `isArchived`, `yieldsInterest`) " +
                    "VALUES ('Wallet', 'ASSET', 'BRL', 'wallet', 0, 0, 0, 0)"
            )
            it.execSQL(
                "INSERT INTO `accounts` (`name`, `type`, `currency`, `iconKey`, `isDefault`, " +
                    "`createdAt`, `isArchived`, `yieldsInterest`) " +
                    "VALUES ('Groceries', 'EXPENSE', 'BRL', 'shopping', 0, 0, 0, 0)"
            )
            it.execSQL("INSERT INTO `dimensions` (`kind`) VALUES ('CATEGORY')")
            it.execSQL("INSERT INTO `transactions` (`title`, `date`) VALUES ('t', '2026-01-01')")
            it.execSQL(
                "INSERT INTO `entries` (`transactionId`, `accountId`, `amount`, `currency`, " +
                    "`dimensionId`) " +
                    "SELECT (SELECT MAX(`id`) FROM `transactions`), " +
                    "(SELECT `id` FROM `accounts` WHERE `name` = 'Groceries'), 1000, 'BRL', " +
                    "(SELECT MAX(`id`) FROM `dimensions`)"
            )
            it.execSQL(
                "INSERT INTO `entries` (`transactionId`, `accountId`, `amount`, `currency`) " +
                    "SELECT (SELECT MAX(`id`) FROM `transactions`), " +
                    "(SELECT `id` FROM `accounts` WHERE `name` = 'Wallet'), -1000, 'BRL'"
            )
        }

        acceptanceOf(file)
    }

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

    /**
     * The counts answer "how much of what I made is in here", and the chart of accounts is
     * not that list.
     *
     * A card is an account in the ledger — a `LIABILITY` one, linked to the facade row —
     * and beyond the user's own the chart holds the system rows the write boundary creates
     * on demand, per currency, which nothing in the app ever renders. Counting the table
     * would tell someone with one bank account and one card that they have two accounts
     * and one card, and it would keep climbing as they spend, with nothing having been
     * created by them.
     */
    @Test
    fun `the counts describe what the user made, not what the ledger keeps`() = runBlocking {
        val file = goodBackup("chart")
        onFile(file) { connection ->
            listOf(
                "'Wallet', 'ASSET'",
                "'Card', 'LIABILITY'",
                "'Expenses', 'EXPENSE'",
                "'Income', 'INCOME'",
                "'Opening balances', 'EQUITY'",
                "'Conversion', 'CONVERSION'",
            ).forEach { row ->
                connection.execSQL(
                    "INSERT INTO `accounts` (`name`, `type`, `currency`, `iconKey`, " +
                        "`isDefault`, `createdAt`, `isArchived`, `yieldsInterest`) " +
                        "VALUES ($row, 'BRL', 'wallet', 0, 0, 0, 0)"
                )
            }
            connection.execSQL(
                "INSERT INTO `credit_cards` (`name`, `limit`, `closingDay`, `dueDay`, " +
                    "`iconKey`, `createdAt`, `accountId`) " +
                    "SELECT 'Card', 1000.0, 1, 10, 'card', 0, `id` " +
                    "FROM `accounts` WHERE `type` = 'LIABILITY'"
            )
        }

        val accepted = acceptanceOf(file)

        assertEquals(
            1L,
            accepted.counts.accounts,
            "six rows in the chart, one account the user opened",
        )
        assertEquals(1L, accepted.counts.creditCards, "and the card is counted as a card")
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

    /**
     * The reason D1 chose "the format is the database itself" over a JSON dump, exercised
     * end to end: a file written by an older build arrives, the migration chain runs over
     * it, and it is accepted as the v14 archive it becomes.
     *
     * A dump would have to recreate the DDL of its own version and let Room migrate that —
     * which is what a `.db` already is, with one serialiser fewer in the way. Restoring it
     * by deserialising into today's entities would skip the chain in silence, new columns
     * taking their defaults and nobody the wiser.
     */
    @Test
    fun `a backup written by an older schema is accepted, and migrated on the way in`() = runBlocking {
        val file = v11Candidate("v11")

        val accepted = acceptanceOf(file)

        assertNull(accepted.origin, "a file this old predates the stamp, and is taken anyway")
        assertEquals(
            14L,
            BundledSQLiteDriver().open(file.absolutePath).use { connection ->
                val statement = connection.prepare("PRAGMA user_version")
                try {
                    statement.step()
                    statement.getLong(0)
                } finally {
                    statement.close()
                }
            },
            "the candidate came out of the gate on this build's schema",
        )
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
