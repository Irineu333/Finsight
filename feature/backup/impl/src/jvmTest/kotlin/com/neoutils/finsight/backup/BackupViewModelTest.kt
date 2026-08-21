@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.backup

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.WindowScope
import androidx.lifecycle.viewModelScope
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import arrow.core.Either
import arrow.core.right
import com.neoutils.finsight.backup.service.JvmBackupFileService
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.entity.CategoryEntity
import com.neoutils.finsight.database.exception.DatabaseVerificationError
import com.neoutils.finsight.database.exception.DatabaseVerificationException
import com.neoutils.finsight.database.getDatabaseBuilder
import com.neoutils.finsight.database.getRoomDatabase
import com.neoutils.finsight.database.snapshot.CandidateVerification
import com.neoutils.finsight.database.snapshot.CandidateVerifier
import com.neoutils.finsight.database.snapshot.captureInto
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.domain.model.BackupPlatform
import com.neoutils.finsight.domain.model.CURRENCY_SEED
import com.neoutils.finsight.domain.model.CaptureOrigin
import com.neoutils.finsight.domain.model.CurrencySeeding
import com.neoutils.finsight.domain.model.DimensionKind
import com.neoutils.finsight.domain.model.SeedCurrency
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.ui.component.ErrorModal
import com.neoutils.finsight.ui.component.SuccessModal
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.ui.screen.backup.BackupAction
import com.neoutils.finsight.ui.screen.backup.BackupUiState
import com.neoutils.finsight.ui.screen.backup.BackupViewModel
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import java.io.File
import java.io.IOException
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
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * The restore flow, over the gate that actually decides.
 *
 * [CandidateVerifier] is the real one, over real files: what these tests are about is
 * *when* the user is asked and what happens to the file afterwards, and both hang on a
 * refusal being a refusal. A stubbed verifier would answer whatever the test told it to,
 * which is the very question being asked. It is stubbed in exactly one test, the one about
 * a verification that could not be carried out: a disk cannot be filled on demand, and
 * what that test is about is the answer the screen gives, not the gate arriving at it.
 *
 * What is faked is the picker — a dialog needs a window and a person — and even there the
 * two file operations that need neither are the real ones, so a temporary this app is
 * supposed to have removed is asserted by asking the filesystem.
 */
class BackupViewModelTest {

    private val temporaries = mutableListOf<File>()

    private fun temporary(name: String): File =
        File.createTempFile("finsight-backup-$name", ".db")
            .also { it.delete(); temporaries += it }

    private fun open(path: String) = getRoomDatabase(
        builder = getDatabaseBuilder(path = path),
        baseCurrency = "BRL",
        currencySeeding = testSeeding(),
    )

    private val liveFile = temporary("live")
    private val live = open(liveFile.absolutePath)
    private val verifier = CandidateVerifier(::open)

    private val files = FakeBackupFileService { temporary("exported") }
    private val modalManager = ModalManager()

    /** What this install would stamp into a file it captured. */
    private val origin = object : CaptureOrigin {
        override val appVersion = "1.2.3"
        override val platform = BackupPlatform.DESKTOP
    }

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        live.close()
        temporaries.forEach { file ->
            listOf("", "-wal", "-shm").forEach { File(file.absolutePath + it).delete() }
        }
    }

    private fun viewModel(gate: CandidateVerifier = verifier) = BackupViewModel(
        database = live,
        candidateVerifier = gate,
        files = files,
        captureOrigin = origin,
        modalManager = modalManager,
        clock = Clock.System,
    )

    /** The state once nothing is running any more. */
    private suspend fun BackupViewModel.idle(): BackupUiState = uiState.first { !it.isBusy }

    /**
     * The state once nothing is running and no file is being held. The candidate is
     * removed before the state stops naming it, so this is also what waiting for the
     * removal looks like.
     */
    private suspend fun BackupViewModel.settled(): BackupUiState =
        uiState.first { !it.isBusy && it.confirmation == null }

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

    private suspend fun AppDatabase.categories(): List<String> =
        categoryDao().observeAllCategories().first().map { it.name }

    /**
     * What the picker hands back: a private copy of a backup, which is what every
     * platform's picker produces and what the verification is allowed to migrate.
     */
    private suspend fun backupOfLive(
        name: String = "candidate",
        appVersion: String = "9.9.9",
        platform: String = "ios",
    ): File = temporary(name).also {
        live.captureInto(it.absolutePath, appVersion = appVersion, platform = platform)
    }

    private fun onFile(file: File, block: (SQLiteConnection) -> Unit) {
        val connection = BundledSQLiteDriver().open(file.absolutePath)
        try {
            block(connection)
        } finally {
            connection.close()
        }
    }

    // --------------------------------------------------------- what was achieved

    /**
     * The export is the one operation whose result the app cannot show: the file left
     * for a place the app does not read, and the screen it was started from is identical
     * before and after. Without a word, a backup that worked and a backup that never
     * happened look the same.
     */
    @Test
    fun `an export the user saved says so`() = runTest {
        live.seedCategory("Mercado")
        val viewModel = viewModel()

        viewModel.onAction(BackupAction.Export(context))
        viewModel.idle()

        assertIs<SuccessModal>(modalManager.top, "the file is somewhere the app cannot show")
    }

    @Test
    fun `an export the user did not save says nothing`() = runTest {
        live.seedCategory("Mercado")
        files.saves = false
        val viewModel = viewModel()

        viewModel.onAction(BackupAction.Export(context))
        viewModel.idle()

        assertNull(
            modalManager.top,
            "closing a picker is not a failure, and telling someone who saved nothing " +
                "that their backup is done is worse than saying nothing at all",
        )
    }

    /**
     * The archive was replaced and the sheet that asked about it is gone, so the screen
     * left behind is the one the user was already looking at. What changed is everywhere
     * except here.
     */
    @Test
    fun `a completed restore says so`() = runTest {
        live.seedCategory("Mercado")
        files.chosen = backupOfLive().absolutePath.right()
        val viewModel = viewModel()

        viewModel.onAction(BackupAction.ChooseFileToRestore(context))
        viewModel.idle()
        viewModel.onAction(BackupAction.Restore)
        viewModel.settled()

        assertIs<SuccessModal>(modalManager.top)
    }

    // ------------------------------------------------------------------ the gate

    @Test
    fun `a file the gate refuses never reaches the confirmation`() = runTest {
        live.seedCategory("Mercado")
        val junk = temporary("junk").also { it.writeBytes(ByteArray(4096) { 0x7A }) }
        files.chosen = junk.absolutePath.right()
        val viewModel = viewModel()

        viewModel.onAction(BackupAction.ChooseFileToRestore(context))
        val state = viewModel.idle()

        assertNull(
            state.confirmation,
            "asking about a file that may still be refused hands over a decision the " +
                "app cannot stand behind",
        )
        assertEquals(
            listOf("Mercado"),
            live.categories(),
            "the archive in use is exactly what it was",
        )
        assertIs<ErrorModal>(modalManager.top, "the refusal is said out loud")
    }

    @Test
    fun `an approved file opens the confirmation carrying its origin and its counts`() = runTest {
        live.seedCategory("Mercado")
        files.chosen = backupOfLive(appVersion = "9.9.9", platform = "ios").absolutePath.right()
        val viewModel = viewModel()

        viewModel.onAction(BackupAction.ChooseFileToRestore(context))
        val state = viewModel.idle()

        val confirmation = assertNotNull(state.confirmation, "the file was approved")
        val origin = assertNotNull(confirmation.origin, "the file carries its stamp")
        assertEquals("9.9.9", origin.appVersion)
        assertEquals(BackupPlatform.IOS, origin.platform)
        assertEquals("ios", origin.platformId)
        assertTrue(origin.createdAt.toEpochMilliseconds() > 0)
        assertEquals(1L, confirmation.counts.categories, "the counts are the file's")
        assertEquals(0L, confirmation.counts.transactions)
    }

    @Test
    fun `an approved file with no stamp is confirmed with its origin unknown`() = runTest {
        live.seedCategory("Mercado")
        val backup = backupOfLive("nostamp")
        onFile(backup) { it.execSQL("DROP TABLE `snapshot_meta`") }
        files.chosen = backup.absolutePath.right()
        val viewModel = viewModel()

        viewModel.onAction(BackupAction.ChooseFileToRestore(context))
        val state = viewModel.idle()

        val confirmation = assertNotNull(
            state.confirmation,
            "a file older than the stamp still restores",
        )
        assertNull(confirmation.origin, "there is nothing to say about where it came from")
        assertEquals(1L, confirmation.counts.categories, "what it holds is known either way")
    }

    /**
     * A verification that could not be carried out has found nothing about the file, and
     * the screen must not answer as though it had.
     *
     * The failure is injected where the gate opens the candidate, because that is the one
     * step of it that touches the device on this side of the copy — and because a machine
     * failure arriving as a [DatabaseVerificationException] rather than as a refusal is
     * the distinction the gate exists to keep. What the user hears is about the check;
     * before this, a full disk was reported as "this file is not a valid backup", which
     * sends someone hunting for a file that would work when no file would.
     */
    @Test
    fun `a gate that could not run is reported, and the screen survives it`() = runTest {
        live.seedCategory("Mercado")
        val backup = backupOfLive()
        files.chosen = backup.absolutePath.right()
        val viewModel = viewModel(
            gate = CandidateVerifier {
                throw DatabaseVerificationException(DatabaseVerificationError.NO_SPACE)
            }
        )

        viewModel.onAction(BackupAction.ChooseFileToRestore(context))
        val state = viewModel.idle()

        assertIs<ErrorModal>(
            modalManager.top,
            "an exception through viewModelScope is a crash on Android, not a message",
        )
        assertNull(state.confirmation, "nothing was approved, so there is nothing to ask")
        assertEquals(
            listOf("Mercado"),
            live.categories(),
            "the archive in use is exactly what it was",
        )
        assertEquals(
            listOf(backup.absolutePath),
            files.discarded,
            "the copy is no use to anyone either way",
        )
    }

    /**
     * The picker is a device, and devices fail in ways this app has no type for. Whatever
     * it raises reaches the user as the failure of the operation they started, rather than
     * as an exception nobody is on the other side of.
     */
    @Test
    fun `a picker that fails outright is reported, not thrown`() = runTest {
        live.seedCategory("Mercado")
        files.copyInFailure = { throw IOException("the device went away") }
        val viewModel = viewModel()

        viewModel.onAction(BackupAction.ChooseFileToRestore(context))
        val state = viewModel.idle()

        assertIs<ErrorModal>(modalManager.top)
        assertNull(state.confirmation)
        assertFalse(state.isVerifying, "the screen is not left waiting on something that ended")
    }

    // ------------------------------------------------------------------ the answer

    @Test
    fun `cancelling the confirmation restores nothing`() = runTest {
        live.seedCategory("Mercado")
        // Taken before "Aluguel" exists, so the two archives are told apart by it.
        files.chosen = backupOfLive().absolutePath.right()
        live.seedCategory("Aluguel")
        val viewModel = viewModel()

        viewModel.onAction(BackupAction.ChooseFileToRestore(context))
        assertNotNull(viewModel.idle().confirmation)

        viewModel.onAction(BackupAction.DiscardCandidate)
        val state = viewModel.settled()

        assertNull(state.confirmation)
        assertEquals(
            listOf("Mercado", "Aluguel"),
            live.categories(),
            "the archive is untouched, and the file that was going to replace it is gone",
        )
    }

    @Test
    fun `confirming replaces the archive with the file's content`() = runTest {
        live.seedCategory("Mercado")
        files.chosen = backupOfLive().absolutePath.right()
        live.seedCategory("Aluguel")
        val viewModel = viewModel()

        viewModel.onAction(BackupAction.ChooseFileToRestore(context))
        assertNotNull(viewModel.idle().confirmation)

        viewModel.onAction(BackupAction.Restore)
        val state = viewModel.settled()

        assertNull(state.confirmation, "there is nothing left to ask about")
        assertEquals(
            listOf("Mercado"),
            live.categories(),
            "the archive became the file's, and the row it did not hold is gone",
        )
    }

    // ------------------------------------------------------- the temporary files

    @Test
    fun `a refused file is removed where it is refused`() = runTest {
        val junk = temporary("junk").also { it.writeBytes(ByteArray(4096) { 0x7A }) }
        files.chosen = junk.absolutePath.right()
        val viewModel = viewModel()

        viewModel.onAction(BackupAction.ChooseFileToRestore(context))
        viewModel.idle()

        assertEquals(listOf(junk.absolutePath), files.discarded)
        assertFalse(junk.exists(), "nobody is coming back for a file the gate turned away")
    }

    @Test
    fun `a cancelled confirmation removes the file it was about`() = runTest {
        live.seedCategory("Mercado")
        val backup = backupOfLive()
        files.chosen = backup.absolutePath.right()
        val viewModel = viewModel()

        viewModel.onAction(BackupAction.ChooseFileToRestore(context))
        viewModel.idle()
        viewModel.onAction(BackupAction.DiscardCandidate)
        viewModel.settled()

        assertEquals(listOf(backup.absolutePath), files.discarded)
        assertNoDatabaseAt(backup)
    }

    @Test
    fun `a completed restore removes the file it was made from`() = runTest {
        live.seedCategory("Mercado")
        val backup = backupOfLive()
        files.chosen = backup.absolutePath.right()
        val viewModel = viewModel()

        viewModel.onAction(BackupAction.ChooseFileToRestore(context))
        viewModel.idle()
        viewModel.onAction(BackupAction.Restore)
        viewModel.settled()

        assertEquals(listOf(backup.absolutePath), files.discarded)
        assertNoDatabaseAt(backup)
    }

    /**
     * Leaving the screen is one tap on a control nothing disables, and by then the
     * capture has already written a complete copy of the database. What the user walked
     * away from is not a pending operation, it is a file.
     */
    @Test
    fun `leaving the screen mid-export removes the file the capture wrote`() = runTest {
        live.seedCategory("Mercado")
        val picker = CompletableDeferred<Unit>()
        files.exportGate = picker
        val viewModel = viewModel()

        viewModel.onAction(BackupAction.Export(context))
        awaitTrue("the capture wrote a file and the picker was offered it") {
            files.handedOut.isNotEmpty()
        }
        val captured = File(files.handedOut.single())
        assertTrue(captured.exists(), "the copy this test is about")

        viewModel.viewModelScope.cancel()
        picker.complete(Unit)

        awaitGone(captured)
    }

    /**
     * The removal itself is held open, so the screen dies while it is in flight. A
     * removal that is skipped because the scope it ran in is gone leaves the whole
     * archive behind, which is the same file the user was told had been discarded.
     */
    @Test
    fun `leaving the screen as the candidate is dropped still removes it`() = runTest {
        live.seedCategory("Mercado")
        val backup = backupOfLive()
        files.chosen = backup.absolutePath.right()
        val viewModel = viewModel()

        viewModel.onAction(BackupAction.ChooseFileToRestore(context))
        viewModel.idle()

        val removal = CompletableDeferred<Unit>()
        files.discardGate = removal
        viewModel.onAction(BackupAction.DiscardCandidate)
        viewModel.viewModelScope.cancel()
        removal.complete(Unit)

        awaitGone(backup)
    }

    /**
     * The gesture this is about is one tap on a control nothing disables, and by the time
     * the sheet is up the candidate is a complete copy of the whole ledger. What the user
     * walked away from is a question; what they left behind is a file, and nothing outside
     * the flow that made it knows where it is.
     */
    @Test
    fun `leaving the screen with the confirmation open removes the file it asks about`() = runTest {
        live.seedCategory("Mercado")
        val backup = backupOfLive()
        files.chosen = backup.absolutePath.right()
        val viewModel = viewModel()

        viewModel.onAction(BackupAction.ChooseFileToRestore(context))
        assertNotNull(
            viewModel.idle().confirmation,
            "the file was approved and is being asked about",
        )

        viewModel.viewModelScope.cancel()

        awaitGone(backup)
        assertEquals(listOf(backup.absolutePath), files.discarded)
        assertEquals(
            listOf("Mercado"),
            live.categories(),
            "a question nobody answered replaced nothing",
        )
    }

    /**
     * The one operation that outlives the screen, because it is the one that cannot be
     * called off: the swap is a single transaction reading from a file the same flow
     * removes as soon as it returns, and the sheet that asked for it refuses to be
     * dismissed while it runs. A screen that goes away mid-replacement finds it done.
     */
    @Test
    fun `leaving the screen as the archive is replaced still replaces it`() = runTest {
        live.seedCategory("Mercado")
        val backup = backupOfLive()
        files.chosen = backup.absolutePath.right()
        // Taken before "Aluguel" exists, so the two archives are told apart by it.
        live.seedCategory("Aluguel")
        val viewModel = viewModel()

        viewModel.onAction(BackupAction.ChooseFileToRestore(context))
        assertNotNull(viewModel.idle().confirmation)

        viewModel.onAction(BackupAction.Restore)
        viewModel.viewModelScope.cancel()

        awaitGone(backup)
        assertEquals(
            listOf("Mercado"),
            live.categories(),
            "the archive became the file's, and the row it did not hold is gone",
        )
    }

    /**
     * The export's own temporary, which exists for one reason: the capture writes to a
     * path, and on two of the three platforms the destination the user picked is not one.
     *
     * The stamp is asserted through the gate that reads it, because it is what carries
     * the two things the feature supplies and `:core:database` cannot know.
     */
    @Test
    fun `an export hands over the captured file and removes the temporary`() = runTest {
        live.seedCategory("Mercado")
        val viewModel = viewModel()

        viewModel.onAction(BackupAction.Export(context))
        viewModel.idle()

        val captured = files.handedOut.single()
        assertEquals(listOf(captured), files.discarded)
        assertNoDatabaseAt(File(captured))

        val exported = assertNotNull(files.exported, "the file reached the destination")
        val verification = verifier.verify(exported.absolutePath)
        val accepted = assertIs<CandidateVerification.Accepted>(
            verification,
            "what this app exports is what this app takes back",
        )
        assertEquals("1.2.3", accepted.origin?.appVersion)
        assertEquals(
            "desktop",
            accepted.origin?.platform,
            "the stamp carries the identifier of the platform, not its diagnostic name",
        )
        assertEquals(1L, accepted.counts.categories)
    }

    /**
     * Waits on real time, because what these tests are waiting for runs on a real
     * dispatcher: the removal is deliberately beyond the reach of the cancelled scope,
     * so no amount of advancing the test clock brings it forward.
     */
    private suspend fun awaitTrue(what: String, condition: () -> Boolean) {
        withContext(Dispatchers.Default) {
            try {
                withTimeout(WAIT_MILLIS) { while (!condition()) delay(POLL_MILLIS) }
            } catch (cause: TimeoutCancellationException) {
                fail(what)
            }
        }
    }

    /** The file, and the journal it may carry, are gone. */
    private suspend fun awaitGone(file: File) {
        awaitTrue("${file.name} is still there") { !File(file.absolutePath).exists() }
        assertNoDatabaseAt(file)
    }

    /**
     * A database is up to three files while it is open in write-ahead logging, and a
     * candidate is opened with Room. Asserting only the main file would pass while two
     * others were left behind.
     */
    private fun assertNoDatabaseAt(file: File) {
        listOf("", "-wal", "-shm").forEach { suffix ->
            assertFalse(
                File(file.absolutePath + suffix).exists(),
                "${file.name}$suffix is still there",
            )
        }
    }
}

/**
 * The picker, without the window and the person a picker needs.
 *
 * The two operations that need neither — a free path for the capture, and removing what
 * this app is done with — are the real desktop service's, so what the tests assert about
 * temporary files is asserted about the code that ships.
 */
private class FakeBackupFileService(
    private val destination: () -> File,
) : BackupFileService {

    private val real = JvmBackupFileService()

    /** What the file picker answers next. */
    var chosen: Either<BackupError, String?> = null.right()

    /** Held open by a test that needs the picker to still be waiting on the user. */
    var exportGate: CompletableDeferred<Unit>? = null

    /** Held open by a test that needs a removal to still be in flight. */
    var discardGate: CompletableDeferred<Unit>? = null

    /** Where the export ended up, standing in for what the user picked. */
    var exported: File? = null

    /** Whether the user picks a destination, or closes the save dialog. */
    var saves: Boolean = true

    val handedOut = mutableListOf<String>()
    val discarded = mutableListOf<String>()

    /** Raised instead of an answer, by a test about a picker that fails outright. */
    var copyInFailure: (() -> Nothing)? = null

    override suspend fun copyInChosenFile(context: PlatformContext): Either<BackupError, String?> {
        copyInFailure?.invoke()
        return chosen
    }

    override suspend fun copyOutCapturedFile(
        sourcePath: String,
        suggestedName: String,
        context: PlatformContext,
    ): Either<BackupError, Boolean> {
        handedOut += sourcePath
        exportGate?.await()
        if (!saves) return false.right()
        exported = destination().also { File(sourcePath).copyTo(it, overwrite = true) }
        return true.right()
    }

    override suspend fun newCapturePath() = real.newCapturePath()

    override suspend fun discard(path: String) {
        discardGate?.await()
        discarded += path
        real.discard(path)
    }
}

private const val WAIT_MILLIS = 10_000L
private const val POLL_MILLIS = 10L

/**
 * The context a picker would be presented from. Nothing in these tests opens one, and the
 * window is never read — which is why it can say so instead of pretending to be one.
 */
private val context = PlatformContext(
    object : WindowScope {
        override val window: ComposeWindow get() = error("no window is presented in a test")
    }
)

/** The seeding with the device taken out of it: the seed, and the code as its own glyph. */
private fun testSeeding() = object : CurrencySeeding {
    override fun rows(): List<SeedCurrency> = CURRENCY_SEED.map { SeedCurrency(it, it) }
    override fun symbolOf(code: String): String = code
}
