@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.backup

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.WindowScope
import arrow.core.Either
import arrow.core.right
import com.neoutils.finsight.backup.service.JvmBackupDestination
import com.neoutils.finsight.backup.service.JvmBackupFolder
import com.neoutils.finsight.backup.service.JvmFolderBackupDestination
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
import com.neoutils.finsight.domain.vault.BackupRetention
import com.neoutils.finsight.domain.vault.ArchiveImport
import com.neoutils.finsight.domain.vault.BackupVault
import com.neoutils.finsight.domain.vault.CaptureOutcome
import com.neoutils.finsight.domain.vault.MigrationOutcome
import com.neoutils.finsight.domain.vault.VaultDestination
import com.neoutils.finsight.domain.vault.VaultDestinations
import com.neoutils.finsight.domain.vault.KeptCopyReader
import com.neoutils.finsight.domain.vault.VaultDestinationChange
import com.neoutils.finsight.domain.vault.VaultFolder
import com.neoutils.finsight.domain.vault.VaultLocation
import com.neoutils.finsight.domain.vault.VaultMigration
import com.neoutils.finsight.domain.vault.VaultPreventiveBackup
import com.neoutils.finsight.domain.vault.VaultSwitch
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.ui.component.Modal
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.ui.modal.carryCopies.CarryCopiesModal
import com.neoutils.finsight.ui.screen.backup.BackupAction
import com.neoutils.finsight.ui.screen.backup.BackupUiState
import com.neoutils.finsight.ui.screen.backup.BackupViewModel
import com.neoutils.finsight.ui.screen.backupHistory.BackupHistoryAction
import com.neoutils.finsight.ui.screen.backupHistory.BackupHistoryUiState
import com.neoutils.finsight.ui.screen.backupHistory.BackupHistoryViewModel
import com.neoutils.finsight.ui.screen.backup.service.BackupDestination
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import com.neoutils.finsight.ui.screen.backup.service.FolderLink
import com.neoutils.finsight.ui.screen.backup.service.OwnCopyCheck
import com.neoutils.finsight.ui.screen.backup.service.StoredBackup
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
import kotlinx.coroutines.delay
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
 * What happens to somebody's copies when the folder they chose goes away, comes back, or is
 * traded for another one.
 *
 * Everything is real — a Room archive, two folders on disk, the gate that reads a copy before
 * removing it — because every claim here is about the file system and not about this code. A
 * folder that has been deleted, a folder that already holds an archive, and a folder being
 * filled by a copy out of another one are all facts a fake would answer however the test
 * wanted; the whole point of the three behaviours under test is that they hold against a disk.
 *
 * The one stand-in is the folder chooser, which no test on any platform can drive. What it
 * would have answered is handed to the same call the dialog feeds, so every rule below it runs.
 */
class FolderRecoveryTest {

    private val temporaries = mutableListOf<File>()

    private val chosen: File = Files.createTempDirectory("finsight-chosen").toFile()

    private val elsewhere: File = Files.createTempDirectory("finsight-elsewhere").toFile()

    private val appStorageFolder: File =
        Files.createTempDirectory("finsight-app-storage").toFile()

    private fun temporary(name: String): File =
        File.createTempFile("finsight-recovery-$name", ".db")
            .also { it.delete(); temporaries += it }

    private fun roomAt(path: String): AppDatabase = getRoomDatabase(
        builder = getDatabaseBuilder(path = path),
        baseCurrency = "BRL",
        currencySeeding = recoverySeeding(),
    )

    private val live = roomAt(temporary("live").absolutePath)

    private val settings = MapSettings()

    private val state = BackupVaultRepository(settings)

    private var instant: Instant = Instant.parse("2026-08-30T10:00:00Z")

    private val verifier = CandidateVerifier(::roomAt)

    private val ownCopy = OwnCopyCheck(verifier)

    /** Which folder the chooser would answer with, moved by the test that changes folders. */
    private var picked: File = chosen

    private val backupFolder = JvmBackupFolder(settings) { picked }

    /** The folder [backupFolder] most recently shifted aside — task 11.10's own rung. */
    private val previousBackupFolder = JvmBackupFolder.previous(settings)

    private val folder = VaultFolder(state = state, folder = backupFolder)

    private var pathsHandedOut = 0

    private val files = object : BackupFileService {

        override suspend fun newCapturePath(): Either<BackupError, String> =
            temporary("vault-${pathsHandedOut++}").absolutePath.right()

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

    private val appStorage = JvmBackupDestination(ownCopy = ownCopy, directory = appStorageFolder)

    private val userFolder =
        JvmFolderBackupDestination(folder = backupFolder, ownCopy = ownCopy, files = files)

    /** The folder rung [userFolder] most recently gave up on — task 11.10's own rung. */
    private val previousUserFolder =
        JvmFolderBackupDestination(folder = previousBackupFolder, ownCopy = ownCopy, files = files)

    private val destinations = VaultDestinations(
        state = state,
        link = folder.link,
        appStorage = appStorage,
        folder = userFolder,
        folderToken = backupFolder,
        previousFolder = previousUserFolder,
        previousFolderToken = previousBackupFolder,
    )

    private val vault = BackupVault(
        vault = state,
        archive = RoomArchiveMark(live),
        destination = destinations,
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

    private val migration = VaultMigration(
        state = state,
        destinations = destinations,
        files = files,
    )

    private val destinationChange = VaultDestinationChange(folder = folder, migration = migration)

    private val context = PlatformContext(
        object : WindowScope {
            override val window: ComposeWindow get() = error("no picker is raised here")
        }
    )

    private val modalManager = ModalManager()

    private fun viewModel() = BackupViewModel(
        database = live,
        archiveRestore = ArchiveRestore(
            database = live,
            verifier = verifier,
            preventive = VaultPreventiveBackup(state, vault),
            vault = vault,
            files = files,
        ),
        files = files,
        destination = destinations,
        captureOrigin = object : CaptureOrigin {
            override val appVersion = "1.2.3"
            override val platform = BackupPlatform.DESKTOP
        },
        vault = state,
        switch = VaultSwitch(state = state, vault = vault),
        folder = folder,
        destinationChange = destinationChange,
        modalManager = modalManager,
        clock = object : Clock {
            override fun now(): Instant = instant
        },
    )

    /**
     * The kept-copies screen, which is where the destination is chosen from. It resolves the
     * same [VaultDestinationChange] the backup screen does, which is the point of the two
     * tests that drive it: the choice moved screens without becoming a second machine.
     */
    private fun historyViewModel() = BackupHistoryViewModel(
        destination = destinations,
        files = files,
        archiveRestore = ArchiveRestore(
            database = live,
            verifier = verifier,
            preventive = VaultPreventiveBackup(state, vault),
            vault = vault,
            files = files,
        ),
        reader = KeptCopyReader(destinations, files, verifier),
        state = state,
        folder = folder,
        vault = vault,
        archiveImport = ArchiveImport(
            state = state,
            destination = destinations,
            verifier = verifier,
            files = files,
            clock = object : Clock {
                override fun now(): Instant = instant
            },
        ),
        destinationChange = destinationChange,
        modalManager = modalManager,
    )

    /** The first state of the copies screen that satisfies [condition]. */
    private suspend fun BackupHistoryViewModel.awaitHistory(
        what: String,
        condition: (BackupHistoryUiState) -> Boolean,
    ): BackupHistoryUiState = withContext(Dispatchers.Default) {
        try {
            withTimeout(WAIT_MILLIS) { uiState.first(condition) }
        } catch (cause: TimeoutCancellationException) {
            fail(what)
        }
    }

    /** The first state that satisfies [condition], or a failure saying [what] never held. */
    private suspend fun BackupViewModel.await(
        what: String,
        condition: (BackupUiState) -> Boolean,
    ): BackupUiState = withContext(Dispatchers.Default) {
        try {
            withTimeout(WAIT_MILLIS) { uiState.first(condition) }
        } catch (cause: TimeoutCancellationException) {
            fail(what)
        }
    }

    /** The first sheet the manager is handed, or a failure saying [what] never went up. */
    private suspend fun awaitOffer(what: String): Modal = withContext(Dispatchers.Default) {
        try {
            withTimeout(WAIT_MILLIS) {
                var top = modalManager.top
                while (top == null) {
                    delay(POLL_MILLIS)
                    top = modalManager.top
                }
                top
            }
        } catch (cause: TimeoutCancellationException) {
            fail(what)
        }
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() {
        live.close()
        temporaries.forEach { file ->
            DATABASE_FILES.forEach { File(file.absolutePath + it).delete() }
        }
        chosen.deleteRecursively()
        elsewhere.deleteRecursively()
        appStorageFolder.deleteRecursively()
    }

    // ------------------------------------------------------------------- the situation

    private suspend fun enter(title: String): Long =
        live.transactionDao().insert(TransactionEntity(title = title, date = DATE))

    private suspend fun pointAt(directory: File) {
        picked = directory
        assertEquals(true, folder.pointAt(context).getOrNull(), "the folder was not taken")
    }

    /** App storage, as a [VaultLocation] — what every carry's other end names. */
    private val appStorageLocation = VaultLocation(VaultDestination.APP_STORAGE, null)

    /**
     * Whichever folder [backupFolder] is pointed at right now, as a [VaultLocation] — read
     * fresh, since [pointAt] moves it (task 11.10).
     */
    private fun folderLocation(): VaultLocation =
        VaultLocation(VaultDestination.USER_FOLDER, backupFolder.identity)

    private suspend fun captureSomethingNew(title: String): String {
        enter(title)
        instant += 1.minutes
        return assertIs<CaptureOutcome.Captured>(vault.captureIfNeeded()).copy.name
    }

    private fun namesIn(directory: File): List<String> =
        directory.listFiles().orEmpty().map { it.name }.sorted()

    private fun namesInApp(): List<String> =
        appStorageFolder.listFiles().orEmpty().map { it.name }.sorted()

    /** The folder is deleted and the app finds out the way it finds out: by asking. */
    private suspend fun folderGoesAway() {
        chosen.deleteRecursively()
        folder.check()
        assertEquals(FolderLink.BROKEN, folder.link.value, "the loss was not noticed")
    }

    // ------------------------------------------- 11.8 · the link falls, and nothing stops

    /**
     * The whole of design D12's promise in one run: the loss is noticed on the opening, the
     * copies go on being taken inside the app, and the choice the person made is still
     * standing when they come to answer the question.
     */
    @Test
    fun `a fallen link keeps the copies coming, inside the app, without touching the choice`() =
        runTest {
            pointAt(chosen)
            state.setOn(true)
            val inFolder = captureSomethingNew("coffee")

            folderGoesAway()

            val provisional = captureSomethingNew("bread")

            assertEquals(
                listOf(provisional),
                namesInApp(),
                "nothing was captured while the folder was gone",
            )
            assertEquals(
                VaultDestination.USER_FOLDER,
                state.observe().value.destination,
                "the app answered the question on the person's behalf",
            )
            assertTrue(folder.rung.isProvisional)
            assertEquals(VaultDestination.APP_STORAGE, folder.rung.inForce)
            assertFalse(chosen.exists(), "something rebuilt the folder that had gone")
            assertTrue(inFolder.isNotEmpty())
        }

    /**
     * The history a person is looking at while the folder is gone is the one they can
     * actually reach — the copies inside the app — and never the folder's, which nothing
     * can read.
     */
    @Test
    fun `while the folder is gone the listing is of the copies inside the app`() = runTest {
        pointAt(chosen)
        state.setOn(true)
        captureSomethingNew("coffee")

        folderGoesAway()
        val provisional = captureSomethingNew("bread")

        assertEquals(
            listOf(provisional),
            assertNotNull(destinations.list().getOrNull()).map { it.name },
        )
    }

    /**
     * The first of the two answers design D12 offers. Pointing at the folder again is the
     * same call as choosing one for the first time (design D4), and it is what un-falls the
     * link: the copies go back to landing in the folder from the next trigger on.
     */
    @Test
    fun `pointing at the folder again sends the copies back to it`() = runTest {
        pointAt(chosen)
        state.setOn(true)
        captureSomethingNew("coffee")
        folderGoesAway()
        captureSomethingNew("bread")

        chosen.mkdirs()
        pointAt(chosen)

        assertEquals(FolderLink.LINKED, folder.link.value)
        assertFalse(folder.rung.isProvisional)

        val back = captureSomethingNew("milk")
        assertEquals(listOf(back), namesIn(chosen))
    }

    /**
     * The second answer. It is a choice and not a fallback: from here the app's own storage
     * is where the person said the copies go, the screen stops calling anything provisional,
     * and the folder stays remembered so that pointing at it again still leads back to the
     * copies in it (design D4).
     */
    @Test
    fun `keeping the copies inside the app answers the question and forgets no folder`() =
        runTest {
            pointAt(chosen)
            state.setOn(true)
            captureSomethingNew("coffee")
            folderGoesAway()

            folder.keepInsideApp()

            assertEquals(VaultDestination.APP_STORAGE, state.observe().value.destination)
            assertFalse(folder.rung.isProvisional, "a choice was still being called provisional")

            chosen.mkdirs()
            pointAt(chosen)
            assertEquals(
                VaultDestination.USER_FOLDER,
                state.observe().value.destination,
                "the folder was forgotten",
            )
        }

    // ---------------------------------------------- 11.9 · pointing at the same folder again

    /**
     * The most valuable thing this feature does, and the one reached by somebody who has just
     * lost everything: a fresh install, pointing at the folder it used to write to, finds the
     * whole archive. Nothing is renewed and nothing is remembered across it — the copies sit
     * directly in the folder, matched by their name, which is the whole of the mechanism
     * (design D4).
     */
    @Test
    fun `a reinstall pointing at the same folder finds the whole history`() = runTest {
        pointAt(chosen)
        state.setOn(true)
        val kept = List(THREE) { captureSomethingNew("entry $it") }.sorted()

        val reinstalled = MapSettings()
        val freshState = BackupVaultRepository(reinstalled)
        val freshFolder = JvmBackupFolder(reinstalled) { chosen }
        val freshVaultFolder = VaultFolder(state = freshState, folder = freshFolder)
        val freshDestinations = VaultDestinations(
            state = freshState,
            link = freshVaultFolder.link,
            appStorage = JvmBackupDestination(ownCopy = ownCopy, directory = appStorageFolder),
            folder = JvmFolderBackupDestination(
                folder = freshFolder,
                ownCopy = ownCopy,
                files = files,
            ),
        )

        assertEquals(
            VaultDestination.APP_STORAGE,
            freshState.observe().value.destination,
            "a fresh install already knew about a folder",
        )
        assertEquals(true, freshVaultFolder.pointAt(context).getOrNull())

        assertEquals(
            kept,
            assertNotNull(freshDestinations.list().getOrNull()).map { it.name }.sorted(),
            "the archive that outlived the install was not found again",
        )
        assertEquals(
            emptyList(),
            chosen.listFiles().orEmpty().filter { it.isDirectory }.map { it.name },
            "pointing again made a folder of the app's own beside the archive",
        )
    }

    /** Pointing at the same folder while already on it changes nothing about the archive. */
    @Test
    fun `pointing at the folder already in use leaves every copy where it is`() = runTest {
        pointAt(chosen)
        state.setOn(true)
        val kept = List(THREE) { captureSomethingNew("entry $it") }.sorted()

        pointAt(chosen)

        assertEquals(kept, namesIn(chosen))
        assertEquals(
            kept,
            assertNotNull(destinations.list().getOrNull()).map { it.name }.sorted(),
        )
    }

    // ------------------------------------- adopting a folder that already holds copies

    /**
     * A copy written straight into the chosen folder, standing in for a previous install
     * that used the same folder before this test's own vault ever pointed at it. It is real
     * content under a real name, aged well before anything this test itself goes on to
     * capture, so ordering by date is never in doubt.
     */
    private suspend fun priorInstallCopies(count: Int): List<String> {
        chosen.mkdirs()
        return List(count) { index ->
            val captured = temporary("prior-$index").absolutePath
            live.captureInto(destinationPath = captured, appVersion = "1.2.3", platform = "desktop")
            val name = "finsight-backup-2020-01-${(index + 1).toString().padStart(2, '0')}T10-00-00.db"
            File(captured).copyTo(File(chosen, name))
            File(chosen, name).setLastModified(PRIOR_INSTALL_EPOCH_MILLIS + index * 1_000L)
            name
        }
    }

    /**
     * The scenario itself. A fresh install turns the vault on — which captures once into
     * its own storage, since nothing has been pointed at yet — and then points at the
     * folder the previous install used, which is already full of that install's own
     * copies. Somebody accepts the offer to carry the fresh copy across, exactly as the
     * screen would let them.
     *
     * The first capture that lands afterward must not be the thing that decides which of
     * those five copies retention has no room for: nobody has even opened the kept-copies
     * screen yet to see what is there.
     */
    @Test
    fun `adopting a folder full of a previous install's copies does not sweep on the first capture`() =
        runTest {
            val previous = priorInstallCopies(FIVE)
            state.setRetention(BackupRetention.FIVE)
            VaultSwitch(state = state, vault = vault).setOn(true)

            picked = chosen
            val offer = assertNotNull(
                destinationChange.pointAtFolder(context).getOrNull(),
                "the app-storage copy taken on enabling should have been offered to carry",
            )
            destinationChange.carry(offer)

            val landed = captureSomethingNew("first entry after adopting")

            val kept = namesIn(chosen)
            assertTrue(
                previous.all { it in kept },
                "a previous install's copy was swept before the person ever saw it: $kept",
            )
            assertTrue(landed in kept, "the capture that was supposed to land is not there")
        }

    /**
     * Retention has not stopped meaning something — it has only been asked to wait one
     * capture. The sweep skipped by adoption is spent, and the very next capture sweeps
     * normally, converging the folder back toward the limit the person chose.
     */
    @Test
    fun `retention resumes on the capture after the one adoption deferred`() = runTest {
        priorInstallCopies(FIVE)
        state.setRetention(BackupRetention.FIVE)
        VaultSwitch(state = state, vault = vault).setOn(true)

        picked = chosen
        val offer = assertNotNull(destinationChange.pointAtFolder(context).getOrNull())
        destinationChange.carry(offer)
        captureSomethingNew("first entry after adopting")

        captureSomethingNew("second entry after adopting")

        assertEquals(
            FIVE,
            namesIn(chosen).size,
            "the folder never converged back to the limit once the deferral was spent",
        )
    }

    /**
     * Pointing at an empty folder — the ordinary case, nothing adopted — is never deferred:
     * copies carried into it afterward are swept on the very first capture that lands. The
     * deferral is about what a folder already held the instant it was pointed at, never
     * about pointing at a folder as such.
     */
    @Test
    fun `pointing at an empty folder does not defer the sweep once copies are carried into it`() =
        runTest {
            state.setOn(true)
            state.setRetention(BackupRetention.EVERYTHING)
            List(SIX) { captureSomethingNew("entry $it") }
            state.setRetention(BackupRetention.FIVE)

            picked = chosen
            val offer = assertNotNull(destinationChange.pointAtFolder(context).getOrNull())
            destinationChange.carry(offer)

            val landed = captureSomethingNew("first entry after pointing at an empty folder")

            assertEquals(
                FIVE,
                namesIn(chosen).size,
                "the first capture into a freshly adopted, empty folder did not sweep",
            )
            assertTrue(landed in namesIn(chosen))
        }

    // ------------------------------------ the root: a rung is not a folder, over real disk

    /**
     * Proved by running code against two real, distinct temp folders: pointing folder A
     * then folder B used to be invisible to the vault, because `VaultRung.inForce` reads
     * `USER_FOLDER` both before and after — the folder underneath changed completely and the
     * rung never moved. Coverage stood over an archive folder B had never seen, and the
     * automatic trigger answered `AlreadyCovered` over a folder that had never been written
     * to at all — the "changing from one folder to another is invisible" defect.
     *
     * **Task 11.10 closes the other half of the same gap.** A now stays readable through the
     * one token [BackupFolder.point] shifted aside on the move to B, so the offer this raises
     * is a real one — not merely non-null, but a carry that actually lands A's copy in B,
     * with A left exactly as it was.
     */
    @Test
    fun `pointing at a genuinely different folder ends coverage and the automatic trigger writes into it`() =
        runTest {
            state.setOn(true)
            picked = chosen
            destinationChange.pointAtFolder(context)
            val landedInA = captureSomethingNew("in the first folder")
            assertEquals(listOf(landedInA), namesIn(chosen))
            assertNotNull(
                state.observe().value.markAtLastCapture,
                "the capture into the first folder was never recorded",
            )

            picked = elsewhere
            val offer = destinationChange.pointAtFolder(context).getOrNull()

            assertNull(
                state.observe().value.markAtLastCapture,
                "coverage from the folder left behind still stood over a folder never written to",
            )
            assertNull(
                state.observe().value.archiveCopy,
                "a copy in the folder left behind was still named as covering the new one",
            )

            val outcome = vault.captureIfNeeded()

            assertIs<CaptureOutcome.Captured>(
                outcome,
                "the automatic trigger answered AlreadyCovered over a folder never captured into",
            )
            assertEquals(1, namesIn(elsewhere).size, "nothing landed in the folder just pointed at")

            // The folder just left (A = chosen) is still readable, through the previous
            // token the move to B (elsewhere) shifted aside — so the copy taken in A is
            // offered for real, and not answered as nothing to carry (task 11.10).
            val realOffer = assertNotNull(
                offer,
                "the folder just left is still readable, and its copy was offered to nobody",
            )
            assertEquals(1, realOffer.copies, "the offer did not count the copy A is holding")

            val carried = destinationChange.carry(realOffer)

            assertEquals(MigrationOutcome.Carried(1), carried)
            assertEquals(
                listOf(landedInA),
                namesIn(chosen),
                "the carry removed a copy from the folder it was only ever supposed to read",
            )
            assertTrue(
                landedInA in namesIn(elsewhere),
                "the copy A was holding never reached the folder now in force",
            )
        }

    /**
     * The lifecycle's other half (task 11.10), driven the way a person actually declines:
     * dismissing the sheet — the "leave" button here, but every other way out of it funnels
     * through the same [com.neoutils.finsight.ui.component.Modal.onDismissed] — never
     * answering it.
     *
     * **This is written to fail against a no-op drop, and it was run against one to prove
     * it.** A first attempt at this test pointed back at A afterward and asserted no offer
     * came up — which passed whether or not the dismissal dropped anything, because A held
     * nothing new by then and, more to the point, *any* further pointing shifts the previous
     * slot on its own (see [VaultFolder.pointAt]'s class comment), overwriting whatever a
     * declined carry did or did not clear before the question could ever be asked again. A
     * test that passes either way is not proof.
     *
     * **So this one never points at anything again.** A is left holding the copy the offer
     * was raised over, and the question the offer was built from — [VaultLocation]s captured
     * before either folder moved, never rebuilt afterward — is put straight to
     * [VaultMigration.carriable]. If the dismissal had not dropped A's token, that call would
     * still resolve it through [VaultDestinations.rungFor]'s *previous* branch and answer
     * with the copy sitting in it; with the token dropped, the same location names a folder
     * this app no longer has a token for, and the answer is empty. Confirmed by reverting
     * [VaultDestinationChange.declineCarry] to a no-op: this failed, reporting the one copy
     * A still holds, exactly as the paragraph above predicts.
     */
    @Test
    fun `dismissing the carry sheet drops the folder just left, proven by what it can no longer offer`() =
        runTest {
            state.setOn(true)
            val screen = viewModel()
            screen.await("the first listing never landed") { it.copies.isRead }

            picked = chosen
            screen.onAction(BackupAction.ChooseFolder(context))
            screen.await("A never became the destination") {
                it.rung.inForce == VaultDestination.USER_FOLDER
            }
            captureSomethingNew("in A")
            val aLocation = folderLocation()

            picked = elsewhere
            screen.onAction(BackupAction.ChooseFolder(context))
            val sheet = assertIs<CarryCopiesModal>(
                awaitOffer("no offer was put up for A's copy"),
                "the sheet that went up was not the offer",
            )
            val bLocation = folderLocation()

            modalManager.dismiss(sheet)
            assertNull(modalManager.top, "the sheet did not actually close")
            assertEquals(1, namesIn(chosen).size, "A was touched by a carry nobody accepted")
            assertEquals(emptyList(), namesIn(elsewhere), "B received a copy nobody accepted")

            // The discriminating check: never a further pointing, which would shift the
            // previous slot on its own regardless of whether the dismissal dropped anything
            // — see the class comment above for why that would prove nothing. Asked directly
            // instead, by the same two locations the declined offer was built from.
            assertEquals(
                emptyList(),
                migration.carriable(aLocation, bLocation),
                "the folder just left kept answering a carry after being declined",
            )
        }

    /**
     * Proved by test: the deferral used to arm on nothing more than "the folder is not
     * empty," with no check that the folder had actually changed — so reconnecting a folder
     * this install already manages, sitting at its own steady state, re-armed a skip that
     * protected nothing this install had not written itself, and cost the next sweep a copy
     * it should have removed.
     */
    @Test
    fun `reconnecting a folder already at its steady state does not defer the next sweep`() =
        runTest {
            state.setOn(true)
            state.setRetention(BackupRetention.FIVE)
            picked = chosen
            destinationChange.pointAtFolder(context)
            repeat(FIVE) { captureSomethingNew("entry $it") }
            assertEquals(FIVE, namesIn(chosen).size, "the folder was not at its steady state yet")

            // The reconnect button's own call (BackupViewModel.chooseFolder /
            // BackupHistoryViewModel), over the folder already in force.
            destinationChange.pointAtFolder(context)

            captureSomethingNew("one more, after reconnecting")

            assertEquals(
                FIVE,
                namesIn(chosen).size,
                "the reconnect armed a deferral over copies this install wrote itself",
            )
        }

    /**
     * Proved by test: the deferral was armed and spent with no destination attached to it
     * at all, so a skip earned by a folder just adopted could be spent by an entirely
     * different destination's sweep — the app's own storage, switched to before the folder
     * that earned the skip ever had a capture land in it.
     */
    @Test
    fun `a deferral armed for the folder is not spent by a sweep in the app's own storage`() =
        runTest {
            state.setOn(true)
            state.setRetention(BackupRetention.EVERYTHING)
            List(SIX) { captureSomethingNew("app storage entry $it") }
            state.setRetention(BackupRetention.FIVE)

            priorInstallCopies(FIVE)
            picked = chosen
            destinationChange.pointAtFolder(context)
            destinationChange.keepInsideApp()

            captureSomethingNew("the first capture back in the app's own storage")

            assertEquals(
                FIVE,
                namesInApp().size,
                "a sweep the app's own storage owed was skipped by a deferral armed for the folder",
            )
        }

    // ------------------------------------------------- 11.10 · carrying the history across

    /**
     * The upgrade path, and the reason the offer exists: the copies taken before a folder was
     * ever pointed at follow the person into it, and none of them leaves the app.
     */
    @Test
    fun `carrying to the folder copies the history and removes nothing`() = runTest {
        state.setOn(true)
        val kept = List(THREE) { captureSomethingNew("entry $it") }.sorted()
        assertEquals(kept, namesInApp())

        pointAt(chosen)

        assertEquals(
            THREE,
            migration.carriable(appStorageLocation, folderLocation()).size,
        )
        val outcome = migration.carry(
            from = appStorageLocation,
            to = folderLocation(),
        )

        assertEquals(MigrationOutcome.Carried(THREE), outcome)
        assertEquals(kept, namesIn(chosen), "the copies did not arrive under their own names")
        assertEquals(kept, namesInApp(), "the source lost a copy to a call that only copies")
    }

    /**
     * Only the newest the destination's retention holds travel: carrying six across so the
     * next sweep can remove one of them is traffic thrown away (design D13). The source keeps
     * all six, because nothing here removes anything.
     */
    @Test
    fun `only what the retention holds is carried`() = runTest {
        state.setOn(true)
        state.setRetention(BackupRetention.EVERYTHING)
        val all = List(SIX) { captureSomethingNew("entry $it") }
        state.setRetention(BackupRetention.FIVE)

        pointAt(chosen)
        val outcome = migration.carry(
            from = appStorageLocation,
            to = folderLocation(),
        )

        assertEquals(MigrationOutcome.Carried(FIVE), outcome)
        assertEquals(all.takeLast(FIVE).sorted(), namesIn(chosen), "the oldest was carried")
        assertEquals(all.sorted(), namesInApp(), "the source was swept by a call that copies")
    }

    /**
     * The order a carry hands the copies over in is the order the destination will read
     * them back in, because every rung but one stamps what it writes with the moment it
     * wrote it. So the history is replayed oldest first: a run that hands the newest copy
     * over first makes it the oldest thing in the new folder, and retention counts from
     * the wrong end.
     */
    @Test
    fun `a carry replays the history oldest first`() = runTest {
        state.setOn(true)
        val kept = List(THREE) { captureSomethingNew("entry $it") }
        pointAt(chosen)

        val recorder = Recording(userFolder)
        val recording = VaultMigration(
            state = state,
            destinations = VaultDestinations(
                state = state,
                link = folder.link,
                appStorage = appStorage,
                folder = recorder,
                folderToken = backupFolder,
            ),
            files = files,
        )

        recording.carry(
            from = appStorageLocation,
            to = folderLocation(),
        )

        assertEquals(kept, recorder.handedOver, "the newest copy was written first")
    }

    /**
     * The whole reason the order matters: the next capture sweeps, and what it sweeps is
     * decided by the order the destination lists in. A history replayed backwards puts the
     * newest copy where `.drop(keep)` reaches it first, and every capture after a change of
     * folder then eats the most recent copy that survived.
     */
    @Test
    fun `the sweep after a carry removes the oldest copy and not the newest`() = runTest {
        state.setOn(true)
        state.setRetention(BackupRetention.EVERYTHING)
        val carried = List(FIVE) { captureSomethingNew("entry $it") }
        state.setRetention(BackupRetention.FIVE)

        pointAt(chosen)
        assertEquals(
            MigrationOutcome.Carried(FIVE),
            migration.carry(
                from = appStorageLocation,
                to = folderLocation(),
            ),
        )

        val next = captureSomethingNew("one more")

        assertEquals(
            (carried.drop(1) + next).sorted(),
            namesIn(chosen),
            "the sweep behind the first capture in the new folder took the newest copy",
        )
    }

    /** A destination that remembers, in order, the names it was asked to write. */
    private class Recording(
        private val delegate: BackupDestination,
    ) : BackupDestination by delegate {

        val handedOver = mutableListOf<String>()

        override suspend fun put(
            capturedPath: String,
            name: String,
        ): Either<BackupError, StoredBackup> {
            handedOver += name
            return delegate.put(capturedPath, name)
        }
    }

    /**
     * The scenario the spec names, and the one design D13 says is the common reason for
     * switching at all: the folder went away, so a new one is chosen. Nothing can be read out
     * of the old one, so nothing is carried — and the switch is not held up by that. What
     * *is* carried is what the app captured inside itself while the question stood, which is
     * the whole point of having gone on capturing.
     */
    @Test
    fun `switching with the old folder gone carries nothing out of it and still switches`() =
        runTest {
            pointAt(chosen)
            state.setOn(true)
            captureSomethingNew("coffee")
            folderGoesAway()
            val provisional = captureSomethingNew("bread")

            assertEquals(
                emptyList(),
                migration.carriable(folderLocation(), appStorageLocation),
                "a folder nothing can read offered copies to carry",
            )

            pointAt(elsewhere)

            assertEquals(FolderLink.LINKED, folder.link.value)
            assertEquals(VaultDestination.USER_FOLDER, state.observe().value.destination)

            val outcome = migration.carry(
                from = appStorageLocation,
                to = folderLocation(),
            )

            assertEquals(MigrationOutcome.Carried(1), outcome)
            assertEquals(listOf(provisional), namesIn(elsewhere))
            assertEquals(listOf(provisional), namesInApp(), "the source was emptied")
        }

    /**
     * Design D13's worst case, stated as a promise: a run that stops partway leaves what it
     * managed in the new destination and every copy in the old one. Duplicate, never loss.
     */
    @Test
    fun `a carry that stops partway leaves the source whole`() = runTest {
        state.setOn(true)
        val kept = List(THREE) { captureSomethingNew("entry $it") }.sorted()
        pointAt(chosen)

        val refusing = VaultMigration(
            state = state,
            destinations = VaultDestinations(
                state = state,
                link = folder.link,
                appStorage = appStorage,
                folder = RefusingAfter(userFolder, puts = 2),
                folderToken = backupFolder,
            ),
            files = files,
        )

        val outcome = refusing.carry(
            from = appStorageLocation,
            to = folderLocation(),
        )

        assertEquals(MigrationOutcome.Interrupted(2, BackupError.EXPORT_FAILED), outcome)
        assertEquals(2, namesIn(chosen).size, "what was copied did not stay copied")
        assertEquals(kept, namesInApp(), "a run that failed took something out of the source")
    }

    /** A destination that stops accepting copies after so many, and answers honestly. */
    private class RefusingAfter(
        private val delegate: BackupDestination,
        private val puts: Int,
    ) : BackupDestination by delegate {

        private var taken = 0

        override suspend fun put(
            capturedPath: String,
            name: String,
        ): Either<BackupError, StoredBackup> {
            if (taken++ >= puts) return Either.Left(BackupError.EXPORT_FAILED)
            return delegate.put(capturedPath, name)
        }
    }

    // ------------------------------------------------------ what the screen is left saying

    /**
     * The wart this rung shipped with: a reading that stands after a failed re-read, met by a
     * change of rung. The count is the app's own storage, the name over it is the folder, and
     * a person reads it as *your copies are in the folder you chose* over a folder that holds
     * nothing.
     *
     * The link is deliberately not re-checked. A folder deleted between two openings is still
     * LINKED as far as the app knows, so the rung is the folder, the listing fails, and the
     * last answer standing is the one taken from the other rung — which is exactly the state
     * a fallen link and a change of destination both produce.
     */
    @Test
    fun `the count of the rung left behind is not shown over the one now in force`() = runTest {
        state.setOn(true)
        List(THREE) { captureSomethingNew("entry $it") }

        val screen = viewModel()
        val first = screen.await("the first listing never landed") { it.copies.isRead }
        assertEquals(THREE, first.copiesInForce.count, "the app's own copies were not counted")

        pointAt(chosen)
        chosen.deleteRecursively()
        screen.onAction(BackupAction.Refresh)

        val after = screen.await("the folder never became the destination") {
            it.rung.inForce == VaultDestination.USER_FOLDER
        }

        assertEquals(THREE, after.copies.count, "the last answer was replaced with zero")
        assertEquals(VaultDestination.APP_STORAGE, after.copies.rung)
        assertFalse(
            after.copiesInForce.isRead,
            "the app's own storage was counted under the name of a folder",
        )
    }

    /**
     * The same reading, when the new rung answers: the count that follows the destination's
     * name is the one taken from it, and never the one left over from the rung before.
     */
    @Test
    fun `a reading of the new rung replaces the one before it`() = runTest {
        state.setOn(true)
        List(THREE) { captureSomethingNew("entry $it") }

        val screen = viewModel()
        screen.await("the first listing never landed") { it.copies.isRead }

        pointAt(chosen)
        screen.onAction(BackupAction.Refresh)

        val after = screen.await("the folder was never read") {
            it.copies.rung == VaultDestination.USER_FOLDER
        }

        assertTrue(after.copiesInForce.isRead)
        assertEquals(0, after.copiesInForce.count, "the app's own count survived the move")
    }

    // -------------------------------------------------------------- the folder's own name

    /**
     * The complaint this rung exists to answer: somebody who pointed at a folder can read
     * which one from the screen, not only that a folder was chosen (design D2 — a display
     * name is not a handle).
     */
    @Test
    fun `the header names the folder somebody actually pointed at`() = runTest {
        pointAt(chosen)
        state.setOn(true)

        val screen = historyViewModel()
        val after = screen.awaitHistory("the folder was never named") { it.folderName != null }

        assertEquals(chosen.name, after.folderName)
    }

    /** The app's own storage has no name to give, and the header must not invent one. */
    @Test
    fun `there is no name over the app's own storage`() = runTest {
        state.setOn(true)

        val screen = historyViewModel()
        val after = screen.awaitHistory("the first listing never landed") { !it.isLoading }

        assertEquals(VaultDestination.APP_STORAGE, after.destination)
        assertNull(after.folderName)
    }

    /**
     * The same guard [BackupUiState.copiesInForce] stands for a count applies to the name: a
     * folder's name must not be shown over copies that are actually landing inside the app
     * while the link is down (design D12).
     */
    @Test
    fun `a fallen link is never named over the app's own storage it fell back to`() = runTest {
        pointAt(chosen)
        state.setOn(true)
        captureSomethingNew("coffee")
        folderGoesAway()

        val screen = historyViewModel()
        val after = screen.awaitHistory("the fallback was never read") {
            it.destination == VaultDestination.APP_STORAGE
        }

        assertNull(after.folderName, "the folder that fell was named over the app's own storage")
    }

    // --------------------------------------------------- nothing moves without being asked

    /**
     * Pointing at a folder offers to carry the history and carries nothing on its own. The
     * offer is the whole of the mechanism: a preference moving is not somebody asking for
     * their backups to be duplicated somewhere (design D13).
     */
    @Test
    fun `pointing at a folder offers to carry, and copies nothing until somebody says so`() =
        runTest {
            state.setOn(true)
            val kept = List(THREE) { captureSomethingNew("entry $it") }.sorted()

            val screen = viewModel()
            screen.await("the first listing never landed") { it.copies.isRead }

            picked = chosen
            screen.onAction(BackupAction.ChooseFolder(context))

            assertIs<CarryCopiesModal>(
                awaitOffer("no offer was put to anybody"),
                "the sheet that went up was not the offer",
            )
            assertEquals(emptyList(), namesIn(chosen), "copies moved without anybody saying so")
            assertEquals(kept, namesInApp())
        }

    /**
     * The other half of the same rule: an offer is only put where there is something to
     * answer. Keeping the copies inside the app because the folder has gone leaves a folder
     * nothing can be read out of, and a sheet asking whether to carry copies out of it would
     * be the app asking a question it already knows the answer to.
     *
     * A negative about a sheet is a negative about something that arrives late, so it is
     * asserted over a window rather than at an instant: the offer in the test above lands
     * well inside it.
     */
    @Test
    fun `answering a fallen link with the app's own storage puts no offer up`() = runTest {
        pointAt(chosen)
        state.setOn(true)
        captureSomethingNew("coffee")
        folderGoesAway()

        val screen = viewModel()
        screen.await("the first listing never landed") { it.copies.isRead }
        screen.onAction(BackupAction.KeepInsideApp)
        screen.await("the choice never moved") {
            it.vault.destination == VaultDestination.APP_STORAGE
        }

        withContext(Dispatchers.Default) { delay(OFFER_WINDOW_MILLIS) }

        assertNull(
            modalManager.top,
            "a sheet went up over a folder nothing can be read from",
        )
    }


    // ------------------------------------------ the destination is chosen where the copies are

    /**
     * The selector's new home. Choosing a folder from the kept-copies screen moves the vault
     * onto it and puts the same offer up — the change is one machine
     * ([VaultDestinationChange]) whichever screen reaches it, and neither screen decides on
     * anybody's behalf what happens to the copies left behind (design D13).
     */
    @Test
    fun `the copies screen chooses the destination and offers to carry`() = runTest {
        state.setOn(true)
        val kept = List(THREE) { captureSomethingNew("entry $it") }.sorted()

        val screen = historyViewModel()
        screen.awaitHistory("the first listing never landed") { !it.isLoading }

        picked = chosen
        screen.onAction(BackupHistoryAction.ChooseFolder(context))

        screen.awaitHistory("the destination never moved") {
            it.destination == VaultDestination.USER_FOLDER
        }
        assertIs<CarryCopiesModal>(
            awaitOffer("no offer was put to anybody"),
            "the sheet that went up was not the offer",
        )
        assertEquals(emptyList(), namesIn(chosen), "copies moved without anybody saying so")
        assertEquals(kept, namesInApp())
    }

    /**
     * The other direction, from the same screen: back inside the app, with the folder still
     * remembered. It is the answer somebody gives to a folder that has gone, and it is
     * offered here as well as on the card that announces the loss.
     */
    @Test
    fun `the copies screen keeps the copies inside the app`() = runTest {
        pointAt(chosen)
        state.setOn(true)
        captureSomethingNew("coffee")

        val screen = historyViewModel()
        screen.awaitHistory("the first listing never landed") { !it.isLoading }

        screen.onAction(BackupHistoryAction.KeepInsideApp)

        screen.awaitHistory("the destination never moved") {
            it.destination == VaultDestination.APP_STORAGE
        }
        assertEquals(
            VaultDestination.APP_STORAGE,
            state.observe().value.destination,
            "the choice was not written",
        )
        assertTrue(chosen.exists(), "the folder somebody chose was destroyed")
    }

    /**
     * The sharp edge of moving the selector: the two ways out of a fallen link are on the
     * backup screen's own card (design D12), not behind a door that a fallen link closes.
     * This is that exit, driven the way the button drives it — while the link is down, from
     * the screen that announces it, with no visit to the copies screen anywhere in it.
     */
    @Test
    fun `a folder that vanished is re-pointed from the backup screen itself`() = runTest {
        pointAt(chosen)
        state.setOn(true)
        captureSomethingNew("coffee")
        folderGoesAway()
        captureSomethingNew("bread")

        val screen = viewModel()
        screen.await("the first listing never landed") { it.copies.isRead }
        assertTrue(screen.uiState.value.rung.isProvisional, "the card had nothing to announce")

        chosen.mkdirs()
        picked = chosen
        screen.onAction(BackupAction.ChooseFolder(context))

        screen.await("the link never came back") { !it.rung.isProvisional }

        val back = captureSomethingNew("milk")
        assertEquals(listOf(back), namesIn(chosen), "the copies did not go back to the folder")
    }

    private companion object {
        val DATE = LocalDate(2026, 8, 30)

        const val WAIT_MILLIS = 5_000L
        const val POLL_MILLIS = 5L

        /** Long enough for the offer the test above waits for, and short enough to run. */
        const val OFFER_WINDOW_MILLIS = 500L

        const val THREE = 3
        const val FIVE = 5
        const val SIX = 6

        /** Long before anything this test itself captures, so ordering is never ambiguous. */
        const val PRIOR_INSTALL_EPOCH_MILLIS = 1_577_836_800_000L // 2020-01-01T00:00:00Z

        val DATABASE_FILES = listOf("", "-wal", "-shm")
    }
}

/** The seeding with the device taken out of it: the seed, and the code as its own glyph. */
private fun recoverySeeding() = object : CurrencySeeding {
    override fun rows(): List<SeedCurrency> = CURRENCY_SEED.map { SeedCurrency(it, it) }
    override fun symbolOf(code: String): String = code
}
