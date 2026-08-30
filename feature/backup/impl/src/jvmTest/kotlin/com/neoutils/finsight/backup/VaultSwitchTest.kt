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
import com.neoutils.finsight.domain.vault.CaptureOutcome
import com.neoutils.finsight.domain.vault.VaultOfferOnce
import com.neoutils.finsight.domain.vault.VaultPreventiveBackup
import com.neoutils.finsight.domain.vault.VaultSwitch
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.feature.backup.api.DestructiveAction
import com.neoutils.finsight.feature.backup.api.VaultOfferState
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import com.neoutils.finsight.ui.screen.backup.service.OwnCopyCheck
import com.russhwolf.settings.MapSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

/**
 * Turning the vault on, which is the occasion the other three triggers cannot cover: the
 * periodic one fires at the next opening and the preventive one before the next deletion,
 * so a vault switched on today would otherwise hold nothing until one of those happens.
 *
 * **The archive and the destination are real**, because both halves of what is under test
 * are facts about them: whether a copy is owed is a claim about `sqlite_sequence`
 * (design D8), and whether one or two files were written is a claim about a folder.
 *
 * The offer path is here rather than in [VaultOfferTest] because what it proves is not the
 * offer — it is what accepting and then deleting produce together, which is one file. Two
 * would be exactly the accumulation design D8 exists to prevent, and this is the one path
 * where two triggers meet within the same second.
 */
class VaultSwitchTest {

    private val temporaries = mutableListOf<File>()

    private val folder: File = Files.createTempDirectory("finsight-switch").toFile()

    private fun temporary(name: String): File =
        File.createTempFile("finsight-capture-$name", ".db")
            .also { it.delete(); temporaries += it }

    private fun roomAt(path: String): AppDatabase = getRoomDatabase(
        builder = getDatabaseBuilder(path = path),
        baseCurrency = "BRL",
        currencySeeding = switchSeeding(),
    )

    private val live = roomAt(temporary("live").absolutePath)

    private val state = BackupVaultRepository(MapSettings())

    private var instant: Instant = Instant.parse("2026-08-30T10:00:00Z")

    private val destination = JvmBackupDestination(
        ownCopy = OwnCopyCheck(CandidateVerifier(::roomAt)),
        directory = folder,
    )

    /** What the app's own temporary area refuses with, when a test wants it to refuse. */
    private var refusePath: BackupError? = null

    private var pathsHandedOut = 0

    private val files = object : BackupFileService {

        override suspend fun newCapturePath(): Either<BackupError, String> =
            refusePath?.left() ?: temporary("switch-${pathsHandedOut++}").absolutePath.right()

        override suspend fun discard(path: String) {
            DATABASE_FILES.forEach { File(path + it).delete() }
        }

        override suspend fun copyInChosenFile(context: PlatformContext) =
            error("turning the vault on never puts a picker in front of anybody")

        override suspend fun copyOutCapturedFile(
            sourcePath: String,
            suggestedName: String,
            context: PlatformContext,
        ) = error("turning the vault on never puts a picker in front of anybody")
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

    private val switch = VaultSwitch(state = state, vault = vault)

    private val preventive = VaultPreventiveBackup(state = state, vault = vault)

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

    // ------------------------------------------------------- turning it on is an occasion

    @Test
    fun `turning the vault on takes the copy it does not have`() = runTest {
        val entered = enter("rent")

        val outcome = switch.setOn(true)

        assertIs<CaptureOutcome.Captured>(outcome, "the vault was turned on and holds nothing")
        assertEquals(1, copies().size)
        assertTrue(holds(outcome.copy.name, entered), "the copy is not of this archive")
    }

    /**
     * The condition is design D8's and not a count of files: a copy that still holds
     * everything the archive does covers it, whether or not the switch has been off in
     * between. Switching off and on again is not a reason to write the same file under a
     * newer name.
     */
    @Test
    fun `turning it on again with a copy that still covers the archive takes none`() = runTest {
        enter("rent")
        assertIs<CaptureOutcome.Captured>(switch.setOn(true))

        switch.setOn(false)
        instant += 1.minutes
        val second = switch.setOn(true)

        assertEquals(CaptureOutcome.AlreadyCovered, second)
        assertEquals(1, copies().size, "the same archive was copied twice")
    }

    /** The switch governs this occasion as it governs the other three (design D1). */
    @Test
    fun `turning it off writes nothing, and asks for nothing to write it into`() = runTest {
        enter("coffee")

        assertEquals(CaptureOutcome.VaultOff, switch.setOn(false))

        assertEquals(emptyList<String>(), copies())
        assertEquals(0, pathsHandedOut, "turning the vault off reached for a path")
    }

    /**
     * A first copy that cannot be written leaves the vault on, and says so.
     *
     * The switch is the person's decision and a full disk is not a reason to undo it — the
     * next trigger writes to the same destination and may well succeed. Reverting it would
     * also disarm the preventive trigger for the deletion that is about to run, which would
     * take the copy *and* the question about going on without one away at once.
     */
    @Test
    fun `a first copy that fails leaves the vault on and reports the failure`() = runTest {
        enter("rent")
        refusePath = BackupError.NO_SPACE

        assertEquals(CaptureOutcome.Failed(BackupError.NO_SPACE), switch.setOn(true))

        assertTrue(state.observe().value.isOn, "the vault was switched off behind the person")
        assertEquals(emptyList<String>(), copies())
    }

    // ------------------------------------------------------------------- the offer path

    /**
     * The offer beside a deletion, accepted: the vault comes on, the copy is taken, and the
     * deletion's own trigger then finds the archive already covered.
     *
     * The clock moves between the two, so a second capture would be a second file rather
     * than a name that collides with the first — the assertion has to be able to fail.
     */
    @Test
    fun `accepting the offer and deleting produce one copy, not two`() = runTest {
        val entered = enter("rent")
        val offer = VaultOfferState(VaultOfferOnce(vault = state, switch = switch))
        assertNotNull(offer.terms, "a vault that is off is offered beside a deletion")

        offer.acceptIfTicked()
        instant += 1.minutes
        preventive.captureBefore(DestructiveAction.DELETE_TRANSACTION)

        assertTrue(state.observe().value.isOn, "accepting turns the whole vault on")
        val copy = assertNotNull(
            copies().singleOrNull(),
            "one deletion accepted from the offer wrote ${copies().size} files",
        )
        assertEquals(1, pathsHandedOut, "the archive was captured more than once")
        assertTrue(holds(copy, entered), "the copy does not hold what was about to go")
    }

    private companion object {
        val DATE = LocalDate(2026, 8, 30)

        /**
         * A database is up to three files while something has it open in write-ahead
         * logging, and reading a copy back opens it with Room.
         */
        val DATABASE_FILES = listOf("", "-wal", "-shm")
    }
}

/** The seeding with the device taken out of it: the seed, and the code as its own glyph. */
private fun switchSeeding() = object : CurrencySeeding {
    override fun rows(): List<SeedCurrency> = CURRENCY_SEED.map { SeedCurrency(it, it) }
    override fun symbolOf(code: String): String = code
}
