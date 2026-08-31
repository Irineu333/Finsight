@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.backup

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import arrow.core.Either
import arrow.core.right
import com.neoutils.finsight.backup.service.JvmBackupDestination
import com.neoutils.finsight.backup.service.JvmMigrationCopyPlace
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.AppSchema
import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.database.getDatabaseBuilder
import com.neoutils.finsight.database.getRoomDatabase
import com.neoutils.finsight.database.repository.BackupVaultRepository
import com.neoutils.finsight.database.repository.RoomArchiveMark
import com.neoutils.finsight.database.snapshot.CandidateVerifier
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.domain.model.BackupPlatform
import com.neoutils.finsight.domain.model.CURRENCY_SEED
import com.neoutils.finsight.domain.model.CaptureOrigin
import com.neoutils.finsight.domain.model.CurrencySeeding
import com.neoutils.finsight.domain.model.SeedCurrency
import com.neoutils.finsight.domain.vault.BackupRetention
import com.neoutils.finsight.domain.vault.BackupVault
import com.neoutils.finsight.domain.vault.CaptureOutcome
import com.neoutils.finsight.domain.vault.VaultPreMigrationCopy
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import com.neoutils.finsight.ui.screen.backup.service.OwnCopyCheck
import com.neoutils.finsight.ui.screen.backup.service.PRE_MIGRATION_BACKUP_NAME
import com.neoutils.finsight.ui.screen.backup.service.STAGED_PRE_MIGRATION_NAME
import com.neoutils.finsight.ui.screen.backup.service.isBackupFileName
import com.russhwolf.settings.MapSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

/**
 * The copy taken before a migration, from the side that decides it happens at all.
 *
 * `:core:database` owns the *order* — the copy precedes the first migration, and its own
 * test proves it. What is claimed here is the other half, the half that module refuses to
 * know: **the switch governs this capture in exactly one place**, and it is the answer given
 * to a module that asks for a path and nothing else (design D11). Off, and there is no path;
 * on with nothing to migrate, and there is no path either — because a path is also what
 * destroys the copy from the last migration, and destroying it on an ordinary opening is the
 * one mistake that costs the file retention was told to spare.
 *
 * And it is claimed over the real desktop place rather than a stub of one, because the last
 * assertion is about a **name**: the copy is spared by retention for carrying the one name
 * reserved for it, so a path ending in anything else would be swept away three periodic
 * copies later — on somebody's device, silently, and nowhere else.
 */
class PreMigrationCopyTest {

    private val temporaries = mutableListOf<File>()

    private val folder: File = Files.createTempDirectory("finsight-migration-copy").toFile()

    private fun temporary(name: String): File =
        File.createTempFile("finsight-migration-$name", ".db")
            .also { it.delete(); temporaries += it }

    /** The file the app serves its archive from, as the place is told about it. */
    private val archive: File = temporary("archive")

    private val state = BackupVaultRepository(MapSettings())

    private val target = VaultPreMigrationCopy(
        state = state,
        place = JvmMigrationCopyPlace(
            archivePath = archive.absolutePath,
            directory = folder,
        ),
    )

    // The vault, for the one test that needs the two to agree about a name.

    private fun roomAt(path: String): AppDatabase = getRoomDatabase(
        builder = getDatabaseBuilder(path = path),
        baseCurrency = "BRL",
        currencySeeding = migrationSeeding(),
    )

    private val live = roomAt(temporary("live").absolutePath)

    private val destination = JvmBackupDestination(
        ownCopy = OwnCopyCheck(CandidateVerifier(::roomAt)),
        directory = folder,
    )

    private var instant: Instant = Instant.parse("2026-08-30T10:00:00Z")

    private var handedOut = 0

    private val files = object : BackupFileService {

        override suspend fun newCapturePath(): Either<BackupError, String> =
            temporary("capture-${handedOut++}").absolutePath.right()

        override suspend fun discard(path: String) {
            DATABASE_FILES.forEach { File(path + it).delete() }
        }

        override suspend fun copyInChosenFile(context: PlatformContext) =
            error("the vault never puts a picker in front of anybody")

        override suspend fun copyOutCapturedFile(
            sourcePath: String,
            suggestedName: String,
            context: PlatformContext,
        ) = error("the vault never puts a picker in front of anybody")
    }

    private val vault = BackupVault(
        vault = state,
        archive = RoomArchiveMark(live),
        destination = destination,
        database = live,
        origin = object : CaptureOrigin {
            override val appVersion = "1.2.3"
            override val platform = BackupPlatform.DESKTOP
        },
        files = files,
        clock = object : Clock {
            override fun now(): Instant = instant
        },
    )

    @AfterTest
    fun tearDown() {
        live.close()
        (temporaries + folder.listFiles().orEmpty()).forEach { file ->
            DATABASE_FILES.forEach { File(file.absolutePath + it).delete() }
        }
        folder.delete()
    }

    // ------------------------------------------------------------------------ the fixtures

    /**
     * An archive declaring [version], with something in it. What decides whether a copy is
     * owed is the version the file carries and nothing else — read in isolation, without
     * Room, precisely so that finding out costs nothing and changes nothing.
     */
    private fun archiveDeclaring(version: Long) {
        BundledSQLiteDriver().open(archive.absolutePath).use { connection ->
            connection.execSQL(
                "CREATE TABLE `kept` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `title` TEXT)"
            )
            connection.execSQL("INSERT INTO `kept` (`title`) VALUES ('$WHAT_THE_USER_HAD')")
            connection.execSQL("PRAGMA user_version = $version")
        }
    }

    /** A copy left by the previous migration, under the one name reserved for it. */
    private fun copyFromTheLastMigration(): File =
        File(folder, PRE_MIGRATION_BACKUP_NAME).apply { writeText(THE_LAST_MIGRATIONS_COPY) }

    /**
     * What `VACUUM INTO` leaves on a volume with no room: a file SQLite opens, that reports
     * `ok`, and that declares no schema and holds nothing.
     */
    private fun emptyDatabaseAt(path: String) {
        BundledSQLiteDriver().open(path).use { connection ->
            connection.execSQL("PRAGMA user_version = 0")
        }
    }

    private fun titleInsideCopy(copy: File): String =
        BundledSQLiteDriver().open(copy.absolutePath).use { connection ->
            connection.scalarText("SELECT `title` FROM `kept`")
        }

    // ------------------------------------------------------- the switch, in the one place

    @Test
    fun `a vault that is off answers nowhere, and leaves the copy it has alone`() {
        archiveDeclaring(OLDER_VERSION)
        val previous = copyFromTheLastMigration()

        assertNull(target.path(), "a vault that is off has nowhere for a copy to go")

        assertTrue(previous.exists(), "and nothing it already had was disturbed")
    }

    /**
     * The other half of why the answer is a path rather than a flag: answering destroys the
     * copy that is there, so it is only answered when something is going to replace it. An
     * opening with nothing to migrate is every opening but one.
     */
    @Test
    fun `an archive already on this version answers nowhere, and keeps the last copy`() {
        archiveDeclaring(AppSchema.VERSION.toLong())
        state.setOn(true)
        val previous = copyFromTheLastMigration()

        assertNull(target.path(), "there is nothing to protect, so there is no path")

        assertTrue(
            previous.exists(),
            "the copy from the last migration went, on an opening that migrated nothing",
        )
    }

    /** A fresh install: nothing to read, nothing to migrate, nothing to copy. */
    @Test
    fun `an archive that is not there yet answers nowhere`() {
        state.setOn(true)

        assertNull(target.path())

        assertFalse(archive.exists(), "and asking must not bring the archive into being")
    }

    // --------------------------------------- the copy in force outlives its replacement

    /**
     * Answering must not destroy the copy that is in force, because nothing guarantees a
     * replacement ever arrives. The `VACUUM` that follows is refused by a full disk — the
     * very condition that makes somebody want the copy — it is swallowed where it happens,
     * and the process can be killed between the two. Whoever is left has neither.
     */
    @Test
    fun `answering leaves the copy from the last migration where it is`() {
        archiveDeclaring(OLDER_VERSION)
        state.setOn(true)
        val previous = copyFromTheLastMigration()

        target.path()

        assertTrue(
            previous.exists(),
            "the copy in force went before anything had replaced it",
        )
    }

    // ------------------------------------------------------------- what the answer is worth

    @Test
    fun `a pending migration answers a path beside the copy in force`() {
        archiveDeclaring(OLDER_VERSION)
        state.setOn(true)
        val previous = copyFromTheLastMigration()

        val path = assertNotNull(target.path(), "the vault is on and the archive is behind")

        assertEquals(
            File(folder, STAGED_PRE_MIGRATION_NAME).absolutePath,
            path,
            "the new copy is written over the one that is still in force",
        )
        assertFalse(
            isBackupFileName(File(path).name),
            "a half-written copy is listed, counted and offered as a way back",
        )
        assertTrue(previous.exists(), "the copy in force went before it had a replacement")
        assertFalse(
            File(path).exists(),
            "`VACUUM INTO` refuses a destination that already holds a file",
        )
    }

    /**
     * The whole road as the app walks it, in the order it walks it: the target answers, the
     * database module takes that answer as its only instruction, and the copy goes in force
     * only once something has been written and read as a database.
     */
    @Test
    fun `the copy in force is replaced once a new one has been written`() {
        archiveDeclaring(OLDER_VERSION)
        state.setOn(true)
        copyFromTheLastMigration()

        getDatabaseBuilder(path = archive.absolutePath, captureInto = target.path())
        target.settle()

        val copy = File(folder, PRE_MIGRATION_BACKUP_NAME)
        assertEquals(
            WHAT_THE_USER_HAD,
            titleInsideCopy(copy),
            "the copy in force is still the one from the migration before",
        )
        assertFalse(
            File(folder, STAGED_PRE_MIGRATION_NAME).exists(),
            "the staged file outlived the copy it became",
        )
    }

    /**
     * The failure this shape exists for. The `VACUUM` is swallowed where it happens, so
     * nothing downstream ever hears about it — and what a person is left with must be the
     * copy they already had.
     */
    @Test
    fun `a copy that was never written leaves the last one in force`() {
        archiveDeclaring(OLDER_VERSION)
        state.setOn(true)
        val previous = copyFromTheLastMigration()

        target.path()
        target.settle()

        assertEquals(
            THE_LAST_MIGRATIONS_COPY,
            previous.readText(),
            "the copy in force went for a replacement that never arrived",
        )
    }

    /**
     * What a full volume actually leaves behind, which is worse than nothing: a database
     * that opens, passes an integrity check and holds no schema and no tables. Under the
     * reserved name it would be listed as a plausible copy, never swept, and offered to
     * somebody as a way back.
     */
    @Test
    fun `a well-formed empty database is not put in force`() {
        archiveDeclaring(OLDER_VERSION)
        state.setOn(true)
        val previous = copyFromTheLastMigration()

        val staged = assertNotNull(target.path())
        emptyDatabaseAt(staged)
        target.settle()

        assertEquals(
            THE_LAST_MIGRATIONS_COPY,
            previous.readText(),
            "an empty database took the place of the copy in force",
        )
        assertFalse(File(staged).exists(), "the file that was refused was left where it was")
    }

    /**
     * The whole road, as the app walks it: the target answers, the database module takes
     * that answer as its only instruction, and what was in the archive is in the copy.
     */
    @Test
    fun `the path the target answers is one the database module captures into`() {
        archiveDeclaring(OLDER_VERSION)
        state.setOn(true)

        getDatabaseBuilder(path = archive.absolutePath, captureInto = target.path())
        target.settle()

        val copy = File(folder, PRE_MIGRATION_BACKUP_NAME)
        assertTrue(copy.exists(), "nothing was written where the target pointed")
        assertEquals(
            WHAT_THE_USER_HAD,
            titleInsideCopy(copy),
            "what the user had did not travel with the copy",
        )
    }

    /** With the vault off it is the same call, and it writes nothing (design D1). */
    @Test
    fun `with the vault off the database module is handed nothing and writes nothing`() {
        archiveDeclaring(OLDER_VERSION)

        getDatabaseBuilder(path = archive.absolutePath, captureInto = target.path())
        target.settle()

        assertFalse(
            File(folder, PRE_MIGRATION_BACKUP_NAME).exists(),
            "the vault is off, and no trigger of it writes anything",
        )
    }

    // -------------------------------------------------------------- and retention spares it

    /**
     * The join the name exists for, over the two things that have to agree about it: the
     * copy this trigger writes, and the sweep that runs behind every capture that lands.
     * Six captures against a limit of five, and what is still there is the copy from
     * before the migration — outside the count, and replaced only by the next migration
     * (design D10).
     */
    @Test
    fun `the copy this trigger writes is the one retention refuses to sweep`() = runTest {
        archiveDeclaring(OLDER_VERSION)
        state.setOn(true)
        state.setRetention(BackupRetention.FIVE)
        getDatabaseBuilder(path = archive.absolutePath, captureInto = target.path())
        target.settle()

        repeat(SIX) { index ->
            instant += 1.minutes
            live.transactionDao().insert(TransactionEntity(title = "entry $index", date = DATE))
            assertIs<CaptureOutcome.Captured>(vault.captureIfNeeded())
        }

        val kept = assertNotNull(destination.list().getOrNull()).map { it.name }
        assertTrue(
            PRE_MIGRATION_BACKUP_NAME in kept,
            "the copy taken before the migration was counted and swept away",
        )
        assertEquals(
            FIVE,
            kept.count { it != PRE_MIGRATION_BACKUP_NAME },
            "and the copies that are in the count are still held to their limit",
        )
    }

    private companion object {
        val DATE = LocalDate(2026, 8, 30)

        const val WHAT_THE_USER_HAD = "what the user had"
        const val THE_LAST_MIGRATIONS_COPY = "the last migration's copy"

        /** Behind this build by one, which is all "there is a migration to run" means. */
        val OLDER_VERSION = AppSchema.VERSION.toLong() - 1

        const val FIVE = 5
        const val SIX = 6

        /**
         * A database is up to three files while something has it open in write-ahead
         * logging, and the confirmation opens a copy with Room.
         */
        val DATABASE_FILES = listOf("", "-wal", "-shm")
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

/** The seeding with the device taken out of it: the seed, and the code as its own glyph. */
private fun migrationSeeding() = object : CurrencySeeding {
    override fun rows(): List<SeedCurrency> = CURRENCY_SEED.map { SeedCurrency(it, it) }
    override fun symbolOf(code: String): String = code
}
