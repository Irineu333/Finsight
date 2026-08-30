@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.backup

import arrow.core.Either
import arrow.core.right
import com.neoutils.finsight.backup.service.JvmBackupDestination
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.database.getDatabaseBuilder
import com.neoutils.finsight.database.getRoomDatabase
import com.neoutils.finsight.database.repository.BackupVaultRepository
import com.neoutils.finsight.database.repository.RoomArchiveMark
import com.neoutils.finsight.database.snapshot.CandidateVerifier
import com.neoutils.finsight.database.snapshot.captureInto
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.domain.model.BackupPlatform
import com.neoutils.finsight.domain.model.CURRENCY_SEED
import com.neoutils.finsight.domain.model.CaptureOrigin
import com.neoutils.finsight.domain.model.CurrencySeeding
import com.neoutils.finsight.domain.model.SeedCurrency
import com.neoutils.finsight.domain.restore.ArchiveRestore
import com.neoutils.finsight.domain.restore.RestoreConfirmation
import com.neoutils.finsight.domain.restore.RestoreOutcome
import com.neoutils.finsight.domain.restore.RestoreQuestions
import com.neoutils.finsight.domain.vault.BackupVault
import com.neoutils.finsight.domain.vault.CaptureOutcome
import com.neoutils.finsight.domain.vault.KeptCopyReader
import com.neoutils.finsight.domain.vault.VaultPreventiveBackup
import com.neoutils.finsight.domain.vault.asArchiveCopy
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import com.neoutils.finsight.ui.screen.backup.service.OwnCopyCheck
import com.neoutils.finsight.ui.screen.backup.service.StoredBackup
import com.neoutils.finsight.ui.screen.backupHistory.BackupHistoryAction
import com.neoutils.finsight.ui.screen.backupHistory.BackupHistoryUiState
import com.neoutils.finsight.ui.screen.backupHistory.BackupHistoryViewModel
import com.neoutils.finsight.util.UiText
import com.russhwolf.settings.MapSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate

/**
 * Which of the kept copies the archive in use *is* — the question the list of copies was
 * silent about, and the one a person asks before restoring an older one.
 *
 * **The archive, the destination and the restore are all real.** What is under test is a
 * claim about two things that only exist as files: a copy the vault wrote, and an archive
 * that has been replaced with one. A faked destination would answer whatever the test
 * wanted about both, and the interesting case — the mark landing on a copy that is *not*
 * the newest — is exactly the case a fake cannot be wrong about.
 *
 * **Coverage is asserted alongside, in the same scenario.** The two facts diverge on
 * purpose right after a restore (`ArchiveCopy`), and the failure that would matter is
 * making them agree again: a restored archive that reads as already covered goes
 * uncaptured, which is a defect this feature has already had once. So every test that
 * proves the mark moved also states what coverage says at that moment.
 */
class CurrentCopyTest {

    private val temporaries = mutableListOf<File>()

    private fun temporary(name: String): File =
        File.createTempFile("finsight-current-$name", ".db")
            .also { it.delete(); temporaries += it }

    private fun open(path: String): AppDatabase = getRoomDatabase(
        builder = getDatabaseBuilder(path = path),
        baseCurrency = "BRL",
        currencySeeding = currentCopySeeding(),
    )

    private val live = open(temporary("live").absolutePath)
    private val verifier = CandidateVerifier(::open)

    private val folder: File = Files.createTempDirectory("finsight-current").toFile()

    private val destination = JvmBackupDestination(
        ownCopy = OwnCopyCheck(verifier),
        directory = folder,
    )

    private val settings = MapSettings()
    private val state = BackupVaultRepository(settings)
    private val modalManager = ModalManager()

    /** Moved by hand, so two copies are never asked for at the same second. */
    private var instant: Instant = Instant.parse("2026-08-30T10:00:00Z")

    private var handedOut = 0

    private val files = object : BackupFileService {

        override suspend fun newCapturePath(): Either<BackupError, String> =
            temporary("out-${handedOut++}").absolutePath.right()

        override suspend fun discard(path: String) {
            DATABASE_FILES.forEach { File(path + it).delete() }
        }

        override suspend fun copyInChosenFile(
            context: PlatformContext,
        ): Either<BackupError, String?> = error("nothing here opens a picker")

        override suspend fun copyOutCapturedFile(
            sourcePath: String,
            suggestedName: String,
            context: PlatformContext,
        ): Either<BackupError, Boolean> = error("nothing here opens a save dialog")
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

    private val archiveRestore = ArchiveRestore(
        database = live,
        verifier = verifier,
        preventive = VaultPreventiveBackup(state, vault),
        vault = vault,
        files = files,
    )

    private fun viewModel() = BackupHistoryViewModel(
        destination = destination,
        files = files,
        archiveRestore = archiveRestore,
        reader = KeptCopyReader(destination, files, verifier),
        vault = state,
        modalManager = modalManager,
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() {
        live.close()
        (temporaries + folder.listFiles().orEmpty()).forEach { file ->
            DATABASE_FILES.forEach { File(file.absolutePath + it).delete() }
        }
        folder.delete()
    }

    // ------------------------------------------------------------------ the fixtures

    /** What the user entering something looks like from here. */
    private suspend fun enter(title: String): Long =
        live.transactionDao().insert(TransactionEntity(title = title, date = DATE))

    /**
     * A copy of the archive as it stands, taken the way the three triggers take one.
     *
     * Time moves first: the name a copy is written under carries the second it was asked
     * for, and two copies asked for in the same one would only differ by a suffix.
     */
    private suspend fun capture(): StoredBackup {
        instant += 1.minutes
        return assertIs<CaptureOutcome.Captured>(vault.captureIfNeeded()).copy
    }

    private suspend fun listed(): List<StoredBackup> = assertNotNull(
        destination.list().getOrNull(),
        "the destination could not be read",
    )

    /**
     * Restores [copy] the way the screen does: the row is tapped, the confirmation comes
     * up, and it is answered.
     */
    private suspend fun BackupHistoryViewModel.restoreFromList(copy: StoredBackup) {
        onAction(BackupHistoryAction.Restore(copy))
        await("the confirmation never came up for ${copy.name}") { it.confirmation != null }
        onAction(BackupHistoryAction.ConfirmRestore)
        await("the restore of ${copy.name} never finished") {
            !it.isBusy && !it.isRestoring && it.confirmation == null
        }
    }

    /** The first state that satisfies [condition], or a failure saying [what] never held. */
    private suspend fun BackupHistoryViewModel.await(
        what: String,
        condition: (BackupHistoryUiState) -> Boolean,
    ): BackupHistoryUiState = withContext(Dispatchers.Default) {
        try {
            withTimeout(WAIT_MILLIS) { uiState.first(condition) }
        } catch (cause: TimeoutCancellationException) {
            fail(what)
        }
    }

    /** The state once the list says [copy] is the one the archive corresponds to. */
    private suspend fun BackupHistoryViewModel.awaitCurrent(
        copy: StoredBackup,
    ): BackupHistoryUiState = await("the list never marked ${copy.name} as the current copy") {
        !it.isLoading && !it.isBusy && it.isCurrent(copy)
    }

    /** The two questions, both answered yes, for the flow driven without a screen. */
    private val saysYes = object : RestoreQuestions {
        override suspend fun confirm(confirmation: RestoreConfirmation) = true
        override suspend fun permitWithoutCopy(reason: UiText) = true
    }

    // ------------------------------------------------------- a copy that was just taken

    @Test
    fun `the copy just taken is the one the archive corresponds to`() = runTest {
        state.setOn(true)
        enter("coffee")
        val copy = capture()

        val ui = viewModel().awaitCurrent(copy)

        assertEquals(1, ui.copies.size, "one capture wrote one copy")
        assertNotNull(
            state.observe().value.archiveCopy,
            "a capture that landed says which copy the archive went into",
        )
    }

    /**
     * The report's own case: two copies, the older one restored, and a list that used to
     * look exactly as it had before.
     */
    @Test
    fun `restoring an older copy marks that copy and not the newest`() = runTest {
        state.setOn(true)
        enter("coffee")
        val older = capture()
        enter("rent")
        val newest = capture()

        val viewModel = viewModel()
        val before = viewModel.awaitCurrent(newest)
        assertFalse(before.isCurrent(older), "the newest copy is where the archive was")

        viewModel.restoreFromList(older)
        val after = viewModel.awaitCurrent(older)

        assertFalse(
            after.isCurrent(newest),
            "the archive is not the newest copy's any more, and the list must not say so",
        )
        assertNull(
            live.transactionDao().getById(RENT_ROW),
            "the archive really is the older copy's content",
        )
    }

    /**
     * Giving coverage up is what makes the restored archive get captured, and it is not
     * what says where the person is standing. The two answer at once here, differently.
     */
    @Test
    fun `a restore marks the copy without letting it cover the archive`() = runTest {
        state.setOn(true)
        enter("coffee")
        val older = capture()
        enter("rent")
        capture()

        viewModel().restoreFromList(older)

        val vault = state.observe().value
        assertEquals(older.asArchiveCopy(), vault.archiveCopy, "the archive came from the older copy")
        assertNull(
            vault.markAtLastCapture,
            "a restored archive is covered by nothing, and the next trigger must capture",
        )
    }

    @Test
    fun `a capture after a restore moves the mark to the copy it wrote`() = runTest {
        state.setOn(true)
        enter("coffee")
        val older = capture()
        enter("rent")
        val newest = capture()

        val viewModel = viewModel()
        viewModel.restoreFromList(older)
        viewModel.awaitCurrent(older)

        val fresh = capture()
        viewModel.onAction(BackupHistoryAction.Refresh)
        val after = viewModel.awaitCurrent(fresh)

        assertFalse(after.isCurrent(older), "the archive stopped being the older copy's")
        assertFalse(after.isCurrent(newest), "and it never was that one's")
    }

    /**
     * The window between giving the old archive up and the new one landing.
     *
     * Both facts are dropped going in, and only one of them is put back coming out. It is
     * the order that decides what a failed replacement leaves behind: cleared first, the
     * list says *nothing* about where the archive is, which is true; recorded first, it
     * would name a copy the archive never became.
     */
    @Test
    fun `nothing describes the archive while it is being replaced`() = runTest {
        state.setOn(true)
        enter("coffee")
        val copy = capture()
        assertEquals(copy.asArchiveCopy(), state.observe().value.archiveCopy)

        vault.archiveReplaced()

        val after = state.observe().value
        assertNull(after.markAtLastCapture, "nothing covers an archive that is being replaced")
        assertNull(
            after.archiveCopy,
            "and no copy describes it either until the replacement has landed",
        )
        assertNotNull(after.lastCapturedAt, "a copy was still taken, and when is still true")
    }

    // -------------------------------------------------------- what nothing may claim

    /**
     * A file from a picker is not a kept copy, so no row of the list describes the archive
     * afterwards. Saying nothing is the answer; the alternative is a mark left on whatever
     * the archive used to be.
     */
    @Test
    fun `restoring a picked file leaves no copy marked`() = runTest {
        state.setOn(true)
        enter("coffee")
        val copy = capture()

        val picked = temporary("picked")
        live.captureInto(picked.absolutePath, appVersion = "9.9.9", platform = "ios")

        val outcome = archiveRestore.restoreFrom(
            candidate = { picked.absolutePath.right() },
            questions = saysYes,
        )

        assertIs<RestoreOutcome.Restored>(outcome)
        assertNull(
            state.observe().value.archiveCopy,
            "no kept copy describes an archive that came from a file the user picked",
        )
        val ui = viewModel().await("the list never settled") { !it.isLoading && !it.isBusy }
        assertFalse(ui.isCurrent(copy), "the copy in the folder is not where the archive is")
    }

    /**
     * The recording names a copy; it never asserts that one exists (design D9). The folder
     * stays the only thing that says what is there, so once the copy the archive came from
     * has left it, no row is marked — and nothing had to go looking for files to make that
     * true.
     */
    @Test
    fun `a copy that left the folder marks no row`() = runTest {
        state.setOn(true)
        enter("coffee")
        val gone = capture()
        enter("rent")
        capture()

        val viewModel = viewModel()
        viewModel.restoreFromList(gone)
        viewModel.awaitCurrent(gone)

        assertTrue(
            assertNotNull(destination.remove(gone).getOrNull()),
            "the copy the archive came from was removed from the folder",
        )
        viewModel.onAction(BackupHistoryAction.Refresh)
        val ui = viewModel.await("the list never dropped the removed copy") { state ->
            !state.isLoading && !state.isBusy && state.copies.none { it.name == gone.name }
        }

        assertNotNull(
            state.observe().value.archiveCopy,
            "the recording is untouched — nothing goes hunting for files to correct it",
        )
        assertTrue(
            ui.copies.none(ui::isCurrent),
            "a recording that outlives its file marks nothing, and must not spill onto " +
                "the copy that is left",
        )
    }

    /**
     * A rewrite under a name already recorded is the one way a name alone could point at
     * the wrong file, and the reserved name a pre-migration copy takes is where it would
     * happen. The instant recorded beside the name is what refuses it.
     */
    @Test
    fun `a different file under the same name is not the copy that was recorded`() = runTest {
        state.setOn(true)
        enter("coffee")
        val copy = capture()

        val impostor = copy.copy(savedAt = copy.savedAt + 1.minutes)

        assertFalse(
            BackupHistoryUiState(archiveCopy = copy.asArchiveCopy()).isCurrent(impostor),
            "the same name written again is a different copy, and is not marked",
        )
    }

    // ---------------------------------------------------------------- the newest copy

    @Test
    fun `the newest copy is the first the destination answered with`() {
        val copies = listOf(newer, older)
        val ui = BackupHistoryUiState(copies = copies)

        assertTrue(ui.isNewest(newer))
        assertFalse(ui.isNewest(older))
    }

    private companion object {
        val DATE = LocalDate(2026, 8, 30)

        /** The second transaction entered, and so the row the older copy cannot hold. */
        const val RENT_ROW = 2L

        const val WAIT_MILLIS = 20_000L

        val newer = StoredBackup(
            name = "finsight-backup-2026-08-30T10-01-00.db",
            savedAt = Instant.parse("2026-08-30T10:01:00Z"),
            sizeInBytes = 1_024,
        )

        val older = StoredBackup(
            name = "finsight-backup-2026-08-29T10-01-00.db",
            savedAt = Instant.parse("2026-08-29T10:01:00Z"),
            sizeInBytes = 1_024,
        )

        /**
         * A database is up to three files while something has it open in write-ahead
         * logging, and the verification opens a candidate with Room.
         */
        val DATABASE_FILES = listOf("", "-wal", "-shm")
    }
}

/** The seeding with the device taken out of it: the seed, and the code as its own glyph. */
private fun currentCopySeeding() = object : CurrencySeeding {
    override fun rows(): List<SeedCurrency> = CURRENCY_SEED.map { SeedCurrency(it, it) }
    override fun symbolOf(code: String): String = code
}
