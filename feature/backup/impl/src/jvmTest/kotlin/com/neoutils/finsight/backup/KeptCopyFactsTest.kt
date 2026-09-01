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
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.domain.model.BackupPlatform
import com.neoutils.finsight.domain.model.CURRENCY_SEED
import com.neoutils.finsight.domain.model.CaptureOrigin
import com.neoutils.finsight.domain.model.CurrencySeeding
import com.neoutils.finsight.domain.model.SeedCurrency
import com.neoutils.finsight.domain.restore.ArchiveRestore
import com.neoutils.finsight.domain.vault.ArchiveImport
import com.neoutils.finsight.domain.vault.BackupVault
import com.neoutils.finsight.domain.vault.CaptureOutcome
import com.neoutils.finsight.domain.vault.KeptCopyFacts
import com.neoutils.finsight.domain.vault.KeptCopyReader
import com.neoutils.finsight.domain.vault.VaultDestinationChange
import com.neoutils.finsight.domain.vault.VaultDestinations
import com.neoutils.finsight.domain.vault.VaultFolder
import com.neoutils.finsight.domain.vault.VaultMigration
import com.neoutils.finsight.domain.vault.VaultPreventiveBackup
import com.neoutils.finsight.domain.vault.service.BackupDestination
import com.neoutils.finsight.domain.vault.service.BackupFileService
import com.neoutils.finsight.domain.vault.service.FolderLink
import com.neoutils.finsight.domain.vault.service.NoBackupFolder
import com.neoutils.finsight.domain.vault.service.OwnCopyCheck
import com.neoutils.finsight.domain.vault.service.StoredBackup
import com.neoutils.finsight.domain.vault.service.UnreachableDestination
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.ui.component.ModalManager
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate

/**
 * What a kept copy says about itself when somebody taps it, and — the half that breaks
 * silently — what the list still refuses to read.
 *
 * **The archive, the destination and the copies are real.** What is under test is a claim
 * about the *content of files*: that the figures in the sheet came out of the copy that was
 * tapped and not out of the archive in use or the copy beside it. A faked destination would
 * answer whatever the test wanted, and the case that matters — two copies of the same
 * archive at two different moments — is exactly the one a fake cannot get wrong.
 *
 * **Every file the flow opens is counted.** The cost of this feature is a file being opened,
 * and the regression it invites is opening one per row: invisible with three copies, ruinous
 * with forty, and impossible to see in a screenshot. So the destination is wrapped in a
 * counter, and the listing is asserted to open nothing at all (design D9).
 */
class KeptCopyFactsTest {

    private val temporaries = mutableListOf<File>()

    private fun temporary(name: String): File =
        File.createTempFile("finsight-facts-$name", ".db")
            .also { it.delete(); temporaries += it }

    private fun open(path: String): AppDatabase = getRoomDatabase(
        builder = getDatabaseBuilder(path = path),
        baseCurrency = "BRL",
        currencySeeding = keptCopySeeding(),
    )

    private val live = open(temporary("live").absolutePath)
    private val verifier = CandidateVerifier(::open)

    private val folder: File = Files.createTempDirectory("finsight-facts").toFile()

    /**
     * The real desktop destination with a turnstile in front of it: every file it hands out
     * is counted, and a test may hold one call open to look at the screen mid-read.
     */
    private val destination = CountingDestination(
        JvmBackupDestination(
            ownCopy = OwnCopyCheck(verifier),
            directory = folder,
        )
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
            override val appVersion = APP_VERSION
            override val platform = BackupPlatform.DESKTOP
        },
        files = files,
        clock = object : Clock {
            override fun now(): Instant = instant
        },
    )

    private val reader = KeptCopyReader(destination, files, verifier)

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
        reader = reader,
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

    // ------------------------------------------------------------------ the fixtures

    private suspend fun enter(title: String): Long =
        live.transactionDao().insert(TransactionEntity(title = title, date = DATE))

    private suspend fun capture(): StoredBackup {
        instant += 1.minutes
        return assertIs<CaptureOutcome.Captured>(vault.captureIfNeeded()).copy
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

    /** The view model with its listing settled, which is where every test starts. */
    private suspend fun listed(): BackupHistoryViewModel = viewModel().also {
        it.await("the listing never settled") { state -> !state.isLoading }
    }

    /**
     * The first reading that is no longer in flight, or a failure saying [what] never came.
     *
     * It is awaited on the flow the sheet reads and not on the screen's state: what one copy
     * holds is the sheet's and the list is not subscribed to it.
     */
    private suspend fun BackupHistoryViewModel.awaitFacts(what: String): KeptCopyFacts =
        withContext(Dispatchers.Default) {
            try {
                withTimeout(WAIT_MILLIS) { facts.first { it != KeptCopyFacts.Reading } }
            } catch (cause: TimeoutCancellationException) {
                fail(what)
            }
        }

    /** What the sheet ends up showing about [copy], once the read has answered. */
    private suspend fun BackupHistoryViewModel.factsOf(copy: StoredBackup): KeptCopyFacts {
        onAction(BackupHistoryAction.Inspect(copy))
        return awaitFacts("the copy ${copy.name} was never described")
    }

    // --------------------------------------------------------- what the list still costs

    /**
     * The regression this whole shape exists to avoid: a listing that opens the files it
     * lists. It is invisible with three copies and unusable with forty, and no screenshot
     * of the screen would ever show it.
     */
    @Test
    fun `opening the list opens no copies at all`() = runTest {
        state.setOn(true)
        enter("coffee")
        capture()
        enter("rent")
        capture()

        val viewModel = listed()
        val ui = viewModel.await("the listing never answered with the two copies") {
            it.copies.size == 2
        }

        assertEquals(
            0,
            destination.handedOut,
            "the list says what the file system says — it opens nothing (design D9)",
        )

        viewModel.factsOf(ui.copies.first())

        assertEquals(
            1,
            destination.handedOut,
            "one tap opens exactly one file: the copy that was tapped",
        )
    }

    // ------------------------------------------------------------- what a copy holds

    /**
     * Two copies of one archive taken at two moments. The figures have to differ, and each
     * has to be the one that was true when *that* file was written — which is the only way
     * of showing they came out of the file rather than out of the archive in use.
     */
    @Test
    fun `a tapped copy says what that copy holds and not what the archive holds`() = runTest {
        state.setOn(true)
        enter("coffee")
        val older = capture()
        enter("rent")
        enter("bread")
        val newest = capture()

        val viewModel = listed()

        val fromOlder = assertIs<KeptCopyFacts.Held>(viewModel.factsOf(older))
        assertEquals(1, fromOlder.counts.transactions, "one entry had been made by then")

        val fromNewest = assertIs<KeptCopyFacts.Held>(viewModel.factsOf(newest))
        assertEquals(3, fromNewest.counts.transactions, "and three by the time of this one")
    }

    /** The stamp `snapshot_meta` carries, which is what the sheet calls the copy's origin. */
    @Test
    fun `a tapped copy says which build and which platform wrote it`() = runTest {
        state.setOn(true)
        enter("coffee")
        val copy = capture()

        val facts = assertIs<KeptCopyFacts.Held>(listed().factsOf(copy))
        val origin = assertNotNull(facts.origin, "a copy this build wrote carries a stamp")

        assertEquals(BackupPlatform.DESKTOP, origin.platform)
        assertEquals(APP_VERSION, origin.appVersion)
    }

    // ---------------------------------------------------- what cannot be read says so

    /**
     * A folder the user can also reach with a file manager is a folder that changes between
     * the listing and the tap. The sheet has to say so — not show an empty box, and not
     * take the screen down.
     */
    @Test
    fun `a copy that left the folder cannot be described`() = runTest {
        state.setOn(true)
        enter("coffee")
        val copy = capture()

        val viewModel = listed()
        assertTrue(File(folder, copy.name).delete(), "the copy was removed from the folder")

        assertEquals(KeptCopyFacts.Unreadable, viewModel.factsOf(copy))
    }

    /** The other way a file stops being describable: it is still there and is not a database. */
    @Test
    fun `a damaged copy cannot be described`() = runTest {
        state.setOn(true)
        enter("coffee")
        val copy = capture()

        val viewModel = listed()
        File(folder, copy.name).writeText("this is not a database")

        assertEquals(KeptCopyFacts.Unreadable, viewModel.factsOf(copy))
    }

    // ------------------------------------------------- the sheet never waits on the read

    /**
     * The sheet goes up on the tap and fills in afterwards, so what it shows while a file is
     * being opened is a state of its own — and it has to be *this* copy's, never the one
     * read before it.
     *
     * The destination is held open mid-read to look at exactly that moment, which is the
     * only way to see it: on a copy small enough for a test, the read is over before
     * anything could be asserted about it.
     */
    @Test
    fun `a second tap starts over instead of showing the copy read before it`() = runTest {
        state.setOn(true)
        enter("coffee")
        val older = capture()
        enter("rent")
        val newest = capture()

        val viewModel = listed()
        assertIs<KeptCopyFacts.Held>(viewModel.factsOf(older))

        val held = CompletableDeferred<Unit>()
        destination.hold = held
        viewModel.onAction(BackupHistoryAction.Inspect(newest))

        assertEquals(
            KeptCopyFacts.Reading,
            viewModel.facts.value,
            "a sheet about the copy being opened must not show the copy opened before it",
        )

        held.complete(Unit)
        val facts = viewModel.awaitFacts("the second copy was never described")
        assertEquals(2, assertIs<KeptCopyFacts.Held>(facts).counts.transactions)
    }

    private companion object {
        val DATE = LocalDate(2026, 8, 30)

        const val APP_VERSION = "1.2.3"

        const val WAIT_MILLIS = 20_000L

        /**
         * A database is up to three files while something has it open in write-ahead
         * logging, and the verification opens a candidate with Room.
         */
        val DATABASE_FILES = listOf("", "-wal", "-shm")
    }
}

/**
 * The destination, with every file it hands out counted and an optional gate in front of
 * [copyOut].
 *
 * It delegates rather than pretends: what is being counted is how often the real thing is
 * asked to produce a file, and a stand-in would be counting itself.
 */
private class CountingDestination(
    private val delegate: BackupDestination,
) : BackupDestination by delegate {

    var handedOut = 0
        private set

    /** Completed by a test to let a read that is being watched finish. */
    var hold: CompletableDeferred<Unit>? = null

    override suspend fun copyOut(
        backup: StoredBackup,
        destinationPath: String,
    ): Either<BackupError, Boolean> {
        handedOut++
        hold?.await()
        return delegate.copyOut(backup, destinationPath)
    }
}

/** The seeding with the device taken out of it: the seed, and the code as its own glyph. */
private fun keptCopySeeding() = object : CurrencySeeding {
    override fun rows(): List<SeedCurrency> = CURRENCY_SEED.map { SeedCurrency(it, it) }
    override fun symbolOf(code: String): String = code
}
