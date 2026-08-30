@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.backup

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.neoutils.finsight.backup.service.JvmBackupDestination
import com.neoutils.finsight.database.AppDatabase
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
import com.neoutils.finsight.domain.vault.BackupVault
import com.neoutils.finsight.domain.vault.VaultPreventiveBackup
import com.neoutils.finsight.domain.vault.VaultPreventiveCoverage
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.feature.backup.api.DestructiveAction
import com.neoutils.finsight.feature.backup.api.DestructiveClass
import com.neoutils.finsight.feature.backup.api.PreventiveCaptureException
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import com.neoutils.finsight.ui.screen.backup.service.OwnCopyCheck
import com.russhwolf.settings.MapSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

/**
 * The preventive trigger, which is a classification and one road from it to the vault.
 *
 * The classification is asserted against the six actions the spec names, one by one, rather
 * than against a count: what this test is for is that the six stay the six, and a count
 * would go on passing while one action swapped places with another.
 *
 * The rest is what a caller sees. An action that is covered is not allowed to go ahead
 * before a copy of what it destroys exists, an action that is not covered must not so much
 * as reach for a path — a deletion the domain already refuses when it would cost typed work
 * has nothing to protect — and a capture that fails has to stop the caller rather than let
 * it destroy something with nothing behind it.
 */
class PreventiveBackupTest {

    private val temporaries = mutableListOf<File>()

    private val folder: File = Files.createTempDirectory("finsight-preventive").toFile()

    private fun temporary(name: String): File =
        File.createTempFile("finsight-capture-$name", ".db")
            .also { it.delete(); temporaries += it }

    private fun roomAt(path: String): AppDatabase = getRoomDatabase(
        builder = getDatabaseBuilder(path = path),
        baseCurrency = "BRL",
        currencySeeding = currencySeeding(),
    )

    private val live = roomAt(temporary("live").absolutePath)

    private val state = BackupVaultRepository(MapSettings())

    private val destination = JvmBackupDestination(
        ownCopy = OwnCopyCheck(CandidateVerifier(::roomAt)),
        directory = folder,
    )

    /** What the app's own temporary area refuses with, when a test wants it to refuse. */
    private var refusePath: BackupError? = null

    private var pathsHandedOut = 0

    private val files = object : BackupFileService {

        override suspend fun newCapturePath(): Either<BackupError, String> =
            refusePath?.left() ?: temporary("preventive-${pathsHandedOut++}").absolutePath.right()

        override suspend fun discard(path: String) {
            DATABASE_FILES.forEach { File(path + it).delete() }
        }

        override suspend fun copyInChosenFile(context: PlatformContext) =
            error("the trigger never puts a picker in front of anybody")

        override suspend fun copyOutCapturedFile(
            sourcePath: String,
            suggestedName: String,
            context: PlatformContext,
        ) = error("the trigger never puts a picker in front of anybody")
    }

    private val preventive = VaultPreventiveBackup(
        state = state,
        vault = BackupVault(
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
                override fun now(): Instant = INSTANT
            },
        ),
    )

    @AfterTest
    fun tearDown() {
        live.close()
        (temporaries + folder.listFiles().orEmpty()).forEach { file ->
            DATABASE_FILES.forEach { File(file.absolutePath + it).delete() }
        }
        folder.delete()
    }

    /** What the user entering something looks like from here. */
    private suspend fun enter(title: String): Long =
        live.transactionDao().insert(TransactionEntity(title = title, date = DATE))

    private suspend fun copies(): List<String> = assertNotNull(
        destination.list().getOrNull(),
        "the destination could not be read",
    ).map { it.name }

    private suspend fun holds(copy: String, id: Long): Boolean {
        val file = File(folder, copy).also { temporaries += it }
        val database = roomAt(file.absolutePath)
        return try {
            database.transactionDao().getById(id) != null
        } finally {
            database.close()
        }
    }

    // ------------------------------------------------------------- the classification

    /**
     * The six of design D7, named here as the spec names them. An action moved into or out
     * of a covered class is a change to what the app protects, and it fails here first.
     */
    @Test
    fun `the covered actions are the six that destroy work nobody types twice`() {
        val covered = DestructiveAction.entries
            .filter { it.classification.isCoveredByPreventiveCapture }

        assertEquals(
            listOf(
                DestructiveAction.RESTORE_BACKUP,
                DestructiveAction.DELETE_TRANSACTION,
                DestructiveAction.DELETE_INSTALLMENT,
                DestructiveAction.DELETE_INVOICE,
                DestructiveAction.DELETE_CURRENCY,
                DestructiveAction.REMOVE_EXCHANGE_RATE,
            ),
            covered,
        )
    }

    /**
     * The classification is a property of the class, so coverage is decided once for each
     * one — an action cannot be an exception inside a class it was put in.
     */
    @Test
    fun `only one class is covered, and it is the one that names typed work`() {
        val covered = DestructiveClass.entries.filter { it.isCoveredByPreventiveCapture }

        assertEquals(listOf(DestructiveClass.TYPED_WORK), covered)
    }

    // --------------------------------------------------------------- what a caller sees

    @Test
    fun `a covered action is preceded by a copy holding what it is about to destroy`() =
        runTest {
            state.setOn(true)
            val entered = enter("rent")

            preventive.captureBefore(DestructiveAction.DELETE_TRANSACTION)

            val copy = copies().singleOrNull()
            assertNotNull(copy, "no copy was taken before a covered action")
            assertTrue(holds(copy, entered), "the copy does not hold what was about to go")
        }

    /**
     * A facade deletion the domain only allows when nothing typed goes with it. The vault
     * is on and there is something to copy; what stops the file is the classification, and
     * it stops it before a path is even asked for.
     */
    @Test
    fun `an action outside the covered class writes nothing and reaches for nothing`() =
        runTest {
            state.setOn(true)
            enter("coffee")

            preventive.captureBefore(DestructiveAction.DELETE_CATEGORY)

            assertEquals(emptyList<String>(), copies())
            assertEquals(0, pathsHandedOut, "an action that is not covered reached the vault")
        }

    /** The switch governs the preventive trigger as it governs the other two (design D1). */
    @Test
    fun `a vault that is off lets the action through, without a copy`() = runTest {
        enter("coffee")

        preventive.captureBefore(DestructiveAction.DELETE_TRANSACTION)

        assertEquals(emptyList<String>(), copies())
    }

    /**
     * The sentence a confirmation shows and the copy the trigger takes are one answer.
     *
     * It is asserted against what the trigger actually did with the file, action by action,
     * because the failure worth catching is the two coming apart: a sheet that stopped
     * calling a deletion permanent while nothing was written would be the false statement
     * this whole rewrite exists to remove.
     */
    @Test
    fun `a confirmation is told a copy is kept exactly when one is`() = runTest {
        state.setOn(true)
        val coverage = VaultPreventiveCoverage(state)

        enter("rent")
        preventive.captureBefore(DestructiveAction.DELETE_TRANSACTION)

        assertEquals(1, copies().size, "a covered action was not preceded by a copy")
        assertTrue(
            coverage.keepsCopyBefore(DestructiveAction.DELETE_TRANSACTION),
            "a copy was written and the sheet was told there was none",
        )

        preventive.captureBefore(DestructiveAction.DELETE_CATEGORY)

        assertEquals(1, copies().size, "an action outside the covered class wrote a file")
        assertFalse(
            coverage.keepsCopyBefore(DestructiveAction.DELETE_CATEGORY),
            "nothing was written and the sheet was told a copy is kept",
        )
    }

    @Test
    fun `a capture that fails stops the action and says why`() = runTest {
        state.setOn(true)
        enter("coffee")
        refusePath = BackupError.NO_SPACE

        val refusal = assertFailsWith<PreventiveCaptureException> {
            preventive.captureBefore(DestructiveAction.DELETE_TRANSACTION)
        }

        assertEquals(BackupError.NO_SPACE.message, refusal.message)
        assertEquals(emptyList<String>(), copies())
    }

    private companion object {
        val DATE = LocalDate(2026, 8, 30)

        val INSTANT: Instant = Instant.parse("2026-08-30T10:00:00Z")

        /**
         * A database is up to three files while something has it open in write-ahead
         * logging, and reading a copy back opens it with Room.
         */
        val DATABASE_FILES = listOf("", "-wal", "-shm")
    }
}

/** The seeding with the device taken out of it: the seed, and the code as its own glyph. */
private fun currencySeeding() = object : CurrencySeeding {
    override fun rows(): List<SeedCurrency> = CURRENCY_SEED.map { SeedCurrency(it, it) }
    override fun symbolOf(code: String): String = code
}
