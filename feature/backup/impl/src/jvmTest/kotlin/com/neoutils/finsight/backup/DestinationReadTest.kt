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
import com.neoutils.finsight.domain.vault.BackupVault
import com.neoutils.finsight.domain.vault.CaptureOutcome
import com.neoutils.finsight.domain.vault.VaultPreventiveBackup
import com.neoutils.finsight.domain.vault.VaultMigration
import com.neoutils.finsight.domain.vault.VaultDestinations
import com.neoutils.finsight.domain.vault.VaultFolder
import com.neoutils.finsight.domain.vault.VaultSwitch
import com.neoutils.finsight.ui.screen.backup.service.FolderLink
import com.neoutils.finsight.ui.screen.backup.service.NoBackupFolder
import com.neoutils.finsight.ui.screen.backup.service.UnreachableDestination
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.ui.screen.backup.BackupAction
import com.neoutils.finsight.ui.screen.backup.BackupUiState
import com.neoutils.finsight.ui.screen.backup.BackupViewModel
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate

/**
 * What the backup screen says the destination holds — which is a reading of a folder, not
 * a table it is subscribed to.
 *
 * **Nothing tells this screen that a file left.** The copies screen sits on top of it, a
 * copy is deleted there, and the route underneath survives in the back stack: read only when
 * the view model was built, the card goes on counting a file that is no longer in the folder,
 * over wording the two screens deliberately share (`BackupLabels`). So the read happens again
 * on the way back, and that is what is pinned here.
 *
 * **And before it has happened, nothing is claimed.** Zero copies and no answer yet are
 * different states of the folder, and only one of them is "no copies yet".
 *
 * The destination is a real folder for the reason every other test of this feature keeps it
 * so: what is under test is whether a second reading sees what the first could not, and a
 * fake would answer whatever the test wanted about both.
 */
class DestinationReadTest {

    private val temporaries = mutableListOf<File>()

    private fun temporary(name: String): File =
        File.createTempFile("finsight-read-$name", ".db")
            .also { it.delete(); temporaries += it }

    private fun open(path: String): AppDatabase = getRoomDatabase(
        builder = getDatabaseBuilder(path = path),
        baseCurrency = "BRL",
        currencySeeding = readSeeding(),
    )

    private val live = open(temporary("live").absolutePath)
    private val verifier = CandidateVerifier(::open)

    private val folder: File = Files.createTempDirectory("finsight-read").toFile()

    private val destination = JvmBackupDestination(
        ownCopy = OwnCopyCheck(verifier),
        directory = folder,
    )

    private val state = BackupVaultRepository(MapSettings())

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

    private val origin = object : CaptureOrigin {
        override val appVersion = "1.2.3"
        override val platform = BackupPlatform.DESKTOP
    }

    private val clock = object : Clock {
        override fun now(): Instant = instant
    }

    private val vault = BackupVault(
        vault = state,
        archive = RoomArchiveMark(live),
        destination = destination,
        database = live,
        origin = origin,
        files = files,
        clock = clock,
    )

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
        destination = destination,
        captureOrigin = origin,
        vault = state,
        switch = VaultSwitch(state = state, vault = vault),
        folder = VaultFolder(state = state, folder = NoBackupFolder),
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
        modalManager = ModalManager(),
        clock = clock,
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

    /** What the user entering something looks like from here. */
    private suspend fun enter(title: String): Long =
        live.transactionDao().insert(TransactionEntity(title = title, date = DATE))

    /** A copy of the archive as it stands, taken the way the three triggers take one. */
    private suspend fun capture(): StoredBackup {
        instant += 1.minutes
        return assertIs<CaptureOutcome.Captured>(vault.captureIfNeeded()).copy
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

    /**
     * The defect's own case: two copies on the card, one deleted from the copies screen on
     * top of it, and a card still saying two once that screen closes.
     *
     * The removal is made on the destination rather than through the other view model
     * because what this screen is owed is the same either way — it did not perform it and
     * nothing told it — and driving two view models would test the pair rather than this
     * one.
     */
    @Test
    fun `coming back to the screen reads the destination again`() = runTest {
        state.setOn(true)
        enter("coffee")
        capture()
        enter("rent")
        val second = capture()

        val viewModel = viewModel()
        viewModel.await("the first listing never landed") { it.copies.count == 2 }

        assertTrue(
            assertNotNull(destination.remove(second).getOrNull()),
            "the copy was removed while another screen was on top",
        )

        viewModel.onAction(BackupAction.Refresh)

        assertEquals(
            1,
            viewModel.await("the card never stopped counting the deleted copy") {
                it.copies.count == 1
            }.copies.count,
        )
    }

    /**
     * Zero copies is an answer; no answer is not. Until the folder has been listed the
     * screen says nothing about it, which is what keeps "no copies yet" from standing over
     * a destination nothing has read.
     */
    @Test
    fun `the destination is not called empty before it has been read`() = runTest {
        state.setOn(true)
        enter("coffee")
        capture()

        assertFalse(BackupUiState().copies.isRead, "a screen that has just opened knows nothing")

        val read = viewModel().await("the listing never landed") { it.copies.isRead }

        assertEquals(1, read.copies.count, "and once it has read, it says what is there")
    }

    private companion object {
        val DATE = LocalDate(2026, 8, 30)

        const val WAIT_MILLIS = 20_000L

        /**
         * A database is up to three files while something has it open in write-ahead
         * logging, and the verification opens a candidate with Room.
         */
        val DATABASE_FILES = listOf("", "-wal", "-shm")
    }
}

/** The seeding with the device taken out of it: the seed, and the code as its own glyph. */
private fun readSeeding() = object : CurrencySeeding {
    override fun rows(): List<SeedCurrency> = CURRENCY_SEED.map { SeedCurrency(it, it) }
    override fun symbolOf(code: String): String = code
}
