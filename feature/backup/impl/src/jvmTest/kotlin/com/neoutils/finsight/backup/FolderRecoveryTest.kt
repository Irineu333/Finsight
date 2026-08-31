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
import com.neoutils.finsight.ui.screen.backup.service.BACKUP_FOLDER_NAME
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

    private val folder = VaultFolder(state = state, folder = backupFolder)

    private val appStorage = JvmBackupDestination(ownCopy = ownCopy, directory = appStorageFolder)

    private val userFolder = JvmFolderBackupDestination(folder = backupFolder, ownCopy = ownCopy)

    private val destinations = VaultDestinations(
        state = state,
        link = folder.link,
        appStorage = appStorage,
        folder = userFolder,
    )

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

    private val context = PlatformContext(
        object : WindowScope {
            override val window: ComposeWindow get() = error("no picker is raised here")
        }
    )

    private val own get() = File(chosen, BACKUP_FOLDER_NAME)

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
        destinationChange = VaultDestinationChange(folder = folder, migration = migration),
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
        destinationChange = VaultDestinationChange(folder = folder, migration = migration),
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

    private suspend fun captureSomethingNew(title: String): String {
        enter(title)
        instant += 1.minutes
        return assertIs<CaptureOutcome.Captured>(vault.captureIfNeeded()).copy.name
    }

    private fun namesIn(directory: File): List<String> =
        File(directory, BACKUP_FOLDER_NAME).listFiles().orEmpty().map { it.name }.sorted()

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
     * whole archive. Nothing is renewed and nothing is remembered across it — the shared
     * subfolder name is the whole of the mechanism (design D4).
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
            folder = JvmFolderBackupDestination(folder = freshFolder, ownCopy = ownCopy),
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
            listOf(BACKUP_FOLDER_NAME),
            chosen.listFiles().orEmpty().filter { it.isDirectory }.map { it.name },
            "pointing again made a second folder beside the archive",
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
            migration.carriable(VaultDestination.APP_STORAGE, VaultDestination.USER_FOLDER).size,
        )
        val outcome = migration.carry(
            from = VaultDestination.APP_STORAGE,
            to = VaultDestination.USER_FOLDER,
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
            from = VaultDestination.APP_STORAGE,
            to = VaultDestination.USER_FOLDER,
        )

        assertEquals(MigrationOutcome.Carried(FIVE), outcome)
        assertEquals(all.takeLast(FIVE).sorted(), namesIn(chosen), "the oldest was carried")
        assertEquals(all.sorted(), namesInApp(), "the source was swept by a call that copies")
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
                migration.carriable(VaultDestination.USER_FOLDER, VaultDestination.APP_STORAGE),
                "a folder nothing can read offered copies to carry",
            )

            pointAt(elsewhere)

            assertEquals(FolderLink.LINKED, folder.link.value)
            assertEquals(VaultDestination.USER_FOLDER, state.observe().value.destination)

            val outcome = migration.carry(
                from = VaultDestination.APP_STORAGE,
                to = VaultDestination.USER_FOLDER,
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
            ),
            files = files,
        )

        val outcome = refusing.carry(
            from = VaultDestination.APP_STORAGE,
            to = VaultDestination.USER_FOLDER,
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

        val DATABASE_FILES = listOf("", "-wal", "-shm")
    }
}

/** The seeding with the device taken out of it: the seed, and the code as its own glyph. */
private fun recoverySeeding() = object : CurrencySeeding {
    override fun rows(): List<SeedCurrency> = CURRENCY_SEED.map { SeedCurrency(it, it) }
    override fun symbolOf(code: String): String = code
}
