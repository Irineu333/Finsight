@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

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
import com.neoutils.finsight.domain.restore.ArchiveRestore
import com.neoutils.finsight.domain.vault.BackupRetention
import com.neoutils.finsight.domain.vault.BackupVault
import com.neoutils.finsight.domain.vault.CaptureOutcome
import com.neoutils.finsight.domain.vault.KeptCopyReader
import com.neoutils.finsight.domain.vault.VaultFolder
import com.neoutils.finsight.domain.vault.VaultPreventiveBackup
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.ui.component.ErrorModal
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.ui.component.SuccessModal
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import com.neoutils.finsight.ui.screen.backup.service.NoBackupFolder
import kotlinx.coroutines.flow.MutableStateFlow
import com.neoutils.finsight.ui.screen.backup.service.UnreachableDestination
import com.neoutils.finsight.ui.screen.backup.service.FolderLink
import com.neoutils.finsight.domain.vault.VaultMigration
import com.neoutils.finsight.domain.vault.VaultDestinations
import com.neoutils.finsight.domain.vault.VaultDestinationChange
import com.neoutils.finsight.domain.vault.ArchiveImport
import com.neoutils.finsight.ui.screen.backup.service.OwnCopyCheck
import com.neoutils.finsight.ui.screen.backup.service.StoredBackup
import com.neoutils.finsight.ui.screen.backupHistory.BackupHistoryAction
import com.neoutils.finsight.ui.screen.backupHistory.BackupHistoryUiState
import com.neoutils.finsight.ui.screen.backupHistory.BackupHistoryViewModel
import com.russhwolf.settings.MapSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
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
 * The copy somebody asked for — the one occasion the vault serves that has a person waiting
 * on the other end of it.
 *
 * **What separates it from the other three is one comparison, and that is the whole subject
 * here.** A trigger nobody chose skips while the copy already in the destination still holds
 * everything the archive does (design D8), and that is what stops a run of twenty deletions
 * leaving twenty identical files. A control that did the same would answer a press with
 * nothing — and the sentence that would explain it is not even true: `budget_categories` and
 * `currencies` issue no generated key, so adding a currency or changing which categories a
 * budget covers moves the archive without moving the mark. So the press captures, and these
 * tests pin exactly that: the case the trigger skips is the case the button must not.
 *
 * **The archive and the destination are real**, for the reason every other test of this
 * feature keeps them so: what is under test is whether a second file arrives in a folder
 * where one already covers everything, and a faked destination would answer whatever the
 * test wanted about both halves.
 *
 * What is faked is the app's own temporary area, and only so that one test can refuse to
 * provide a path and another can hold one back long enough for a second press to land.
 */
class CaptureNowTest {

    private val temporaries = mutableListOf<File>()

    private fun temporary(name: String): File =
        File.createTempFile("finsight-capture-now-$name", ".db")
            .also { it.delete(); temporaries += it }

    private fun open(path: String): AppDatabase = getRoomDatabase(
        builder = getDatabaseBuilder(path = path),
        baseCurrency = "BRL",
        currencySeeding = captureNowSeeding(),
    )

    private val live = open(temporary("live").absolutePath)
    private val verifier = CandidateVerifier(::open)

    private val folder: File = Files.createTempDirectory("finsight-capture-now").toFile()

    private val destination = JvmBackupDestination(
        ownCopy = OwnCopyCheck(verifier),
        directory = folder,
    )

    private val settings = MapSettings()
    private val state = BackupVaultRepository(settings)
    private val modalManager = ModalManager()

    /** Moved by hand, so two copies are never asked for in the same second. */
    private var instant: Instant = Instant.parse("2026-08-30T10:00:00Z")

    /** How many times a capture got as far as asking for somewhere to write. */
    private var handedOut = 0

    /** What the app's own temporary area refuses with, when a test wants it to refuse. */
    private var refuse: BackupError? = null

    /**
     * Held open while a test wants a capture to still be running — which is the only way to
     * press the control a second time before the first press has finished.
     */
    private var held: CompletableDeferred<Unit>? = null

    private val files = object : BackupFileService {

        override suspend fun newCapturePath(): Either<BackupError, String> {
            held?.await()
            refuse?.let { return it.left() }
            return temporary("out-${handedOut++}").absolutePath.right()
        }

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

    private val vaultFolder = VaultFolder(state = state, folder = NoBackupFolder)

    /**
     * Nothing here changes destination, and this is what says so: one rung, and a folder
     * that cannot be reached, so no move and no offer is ever produced.
     */
    private val destinationChange = VaultDestinationChange(
        folder = vaultFolder,
        migration = VaultMigration(
            state = state,
            destinations = VaultDestinations(
                state = state,
                link = MutableStateFlow(FolderLink.NONE),
                appStorage = destination,
                folder = UnreachableDestination,
            ),
            files = files,
        ),
    )

    private val archiveImport = ArchiveImport(
        state = state,
        destination = destination,
        verifier = verifier,
        files = files,
        clock = object : Clock {
            override fun now(): Instant = instant
        },
    )

    private fun viewModel() = BackupHistoryViewModel(
        destination = destination,
        files = files,
        archiveRestore = ArchiveRestore(
            database = live,
            verifier = verifier,
            preventive = VaultPreventiveBackup(state, vault),
            vault = vault,
            files = files,
        ),
        reader = KeptCopyReader(destination, files, verifier),
        state = state,
        folder = vaultFolder,
        vault = vault,
        archiveImport = archiveImport,
        destinationChange = destinationChange,
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

    // ------------------------------------------------------------------- the fixtures

    /** What the user entering something looks like from here. */
    private suspend fun enter(title: String): Long =
        live.transactionDao().insert(TransactionEntity(title = title, date = DATE))

    /** One occasion on which a trigger — not a person — asks the vault for a copy. */
    private suspend fun trigger(): CaptureOutcome {
        instant += 1.minutes
        return vault.captureIfNeeded()
    }

    /** One press of the control, at a moment of its own. */
    private suspend fun press(): CaptureOutcome {
        instant += 1.minutes
        return vault.captureNow()
    }

    private suspend fun listed(): List<String> = assertNotNull(
        destination.list().getOrNull(),
        "the destination could not be read",
    ).map { it.name }

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

    // ------------------------------------------------- the comparison the press skips

    /**
     * The whole decision, in one scenario: the same archive, at the same instant, answered
     * two ways. A trigger has nothing to protect and says so; a press writes the file.
     */
    @Test
    fun `a copy asked for is taken where the one already there covers the archive`() = runTest {
        state.setOn(true)
        enter("coffee")
        val covering = assertIs<CaptureOutcome.Captured>(trigger()).copy
        assertEquals(
            CaptureOutcome.AlreadyCovered,
            trigger(),
            "nothing was entered, so no trigger of the three would take another copy",
        )

        val pressed = press()

        assertIs<CaptureOutcome.Captured>(pressed)
        assertEquals(2, listed().size, "the press wrote a file the trigger would not have")
        assertNotEquals(covering.name, pressed.copy.name)
    }

    /**
     * A copy asked for is a copy, and retention does not ask where it came from: six
     * presses against a limit of five leave five files, the oldest gone.
     */
    @Test
    fun `a copy asked for is counted by retention like any other`() = runTest {
        state.setOn(true)
        state.setRetention(BackupRetention.FIVE)
        enter("coffee")

        val taken = List(SIX) { assertIs<CaptureOutcome.Captured>(press()).copy.name }

        assertEquals(SIX, taken.toSet().size, "six presses are six copies")
        assertEquals(FIVE, listed().size)
        assertEquals(taken.drop(1).toSet(), listed().toSet(), "the oldest is the one that goes")
    }

    /**
     * Design D1 holds for this intent as much as for the three triggers, and it holds in
     * the vault rather than in the screen that offers it: the control only appears while
     * the vault is on, but that is a fact about today's navigation, not a guarantee.
     */
    @Test
    fun `a copy asked for with the vault off writes nothing, and reaches for nothing`() =
        runTest {
            enter("coffee")

            assertEquals(CaptureOutcome.VaultOff, press())

            assertEquals(0, handedOut, "a vault that is off does not even reach for a path")
            assertEquals(emptyList(), listed())
        }

    // ------------------------------------------------------------------ from the screen

    /**
     * What the person sees: the file arrives in the list, and the mark that says where the
     * app is standing moves onto it — because it is where the app is standing.
     */
    @Test
    fun `the copy asked for lands in the list and takes the current mark`() = runTest {
        state.setOn(true)
        enter("coffee")
        val automatic = assertIs<CaptureOutcome.Captured>(trigger()).copy

        val viewModel = viewModel()
        viewModel.await("the list never marked the copy the trigger took") {
            !it.isLoading && it.isCurrent(automatic)
        }

        instant += 1.minutes
        viewModel.onAction(BackupHistoryAction.Capture)
        val ui = viewModel.await("the copy asked for never reached the list") {
            !it.isLoading && !it.isBusy && it.copies.size == 2
        }

        assertTrue(ui.isCurrent(ui.copies.first()), "the copy just taken is where the app is")
        assertFalse(ui.isCurrent(automatic), "the older copy is not, and must not say it is")
        assertIs<SuccessModal>(modalManager.top, "a copy that landed is said to have landed")
    }

    /**
     * The second press, while the first is still writing. It produces nothing at all —
     * not a second file, and not so much as a request for somewhere to put one.
     */
    @Test
    fun `a second press while one is running produces nothing`() = runTest {
        state.setOn(true)
        enter("coffee")

        val gate = CompletableDeferred<Unit>()
        held = gate

        val viewModel = viewModel()
        viewModel.await("the listing never settled") { !it.isLoading }

        viewModel.onAction(BackupHistoryAction.Capture)
        viewModel.await("the capture never started") { it.isCapturing }
        viewModel.onAction(BackupHistoryAction.Capture)

        gate.complete(Unit)
        val ui = viewModel.await("the capture never reached the list") {
            !it.isLoading && !it.isBusy && it.copies.isNotEmpty()
        }

        assertEquals(1, handedOut, "the second press must not start a capture of its own")
        assertEquals(1, ui.copies.size)
        assertEquals(1, listed().size)
    }

    /**
     * A capture that did not happen is said, and is never dressed as one that did: the
     * success is the only thing a copy in the destination is allowed to produce.
     */
    @Test
    fun `a capture that fails is reported, and shows no success`() = runTest {
        state.setOn(true)
        enter("coffee")
        refuse = BackupError.EXPORT_FAILED

        val viewModel = viewModel()
        viewModel.await("the listing never settled") { !it.isLoading }

        viewModel.onAction(BackupHistoryAction.Capture)
        viewModel.await("the capture never finished") { !it.isLoading && !it.isBusy }

        assertIs<ErrorModal>(modalManager.top, "a refusal is said out loud")
        assertEquals(emptyList(), listed(), "a capture that failed leaves no file")
        assertNull(
            state.observe().value.archiveCopy,
            "nothing landed, so no copy is the one the archive corresponds to",
        )
    }

    private companion object {
        val DATE = LocalDate(2026, 8, 30)

        const val FIVE = 5
        const val SIX = 6

        const val WAIT_MILLIS = 20_000L

        /**
         * A database is up to three files while something has it open in write-ahead
         * logging, and the verification opens a candidate with Room.
         */
        val DATABASE_FILES = listOf("", "-wal", "-shm")
    }
}

/** The seeding with the device taken out of it: the seed, and the code as its own glyph. */
private fun captureNowSeeding() = object : CurrencySeeding {
    override fun rows(): List<SeedCurrency> = CURRENCY_SEED.map { SeedCurrency(it, it) }
    override fun symbolOf(code: String): String = code
}
