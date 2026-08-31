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
import com.neoutils.finsight.domain.vault.ArchiveImport
import com.neoutils.finsight.domain.vault.BackupVault
import com.neoutils.finsight.domain.vault.CaptureOutcome
import com.neoutils.finsight.domain.vault.KeptCopyReader
import com.neoutils.finsight.domain.vault.VaultDestinationChange
import com.neoutils.finsight.domain.vault.VaultDestinations
import com.neoutils.finsight.domain.vault.VaultFolder
import com.neoutils.finsight.domain.vault.VaultMigration
import com.neoutils.finsight.domain.vault.VaultPreventiveBackup
import com.neoutils.finsight.domain.vault.asArchiveCopy
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import com.neoutils.finsight.ui.screen.backup.service.FolderLink
import com.neoutils.finsight.ui.screen.backup.service.NoBackupFolder
import com.neoutils.finsight.ui.screen.backup.service.OwnCopyCheck
import com.neoutils.finsight.ui.screen.backup.service.PRE_MIGRATION_BACKUP_NAME
import com.neoutils.finsight.ui.screen.backup.service.StoredBackup
import com.neoutils.finsight.ui.screen.backup.service.UnreachableDestination
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate

/**
 * Taking a kept copy away is a deliberate act, and this is what says so.
 *
 * A removal destroys the one thing on the copies screen that cannot be made again: the
 * history is a reading of the folder rather than a record (design D9), the file is not moved
 * to a bin, and nothing anywhere in the feature undoes it. Every other destructive action in
 * this app puts a question up first, and a list of backups is exactly where one stray tap
 * costs the most.
 *
 * **The archive, the destination and the vault are all real**, because the claims here are
 * about files: that the file is still in the folder while the question stands, that it is
 * gone once the question is answered, and that the vault is told either way. A faked
 * destination would answer whatever this test wanted about all three.
 */
class RemoveCopyTest {

    private val temporaries = mutableListOf<File>()

    private fun temporary(name: String): File =
        File.createTempFile("finsight-remove-$name", ".db")
            .also { it.delete(); temporaries += it }

    private fun open(path: String): AppDatabase = getRoomDatabase(
        builder = getDatabaseBuilder(path = path),
        baseCurrency = "BRL",
        currencySeeding = removeCopySeeding(),
    )

    private val live = open(temporary("live").absolutePath)
    private val verifier = CandidateVerifier(::open)

    private val folder: File = Files.createTempDirectory("finsight-remove").toFile()

    private val destination = JvmBackupDestination(
        ownCopy = OwnCopyCheck(verifier),
        directory = folder,
    )

    private val state = BackupVaultRepository(MapSettings())
    private val modalManager = ModalManager()

    /** Moved by hand, so two copies are never asked for at the same second. */
    private var instant: Instant = Instant.parse("2026-08-30T10:00:00Z")

    private val clock = object : Clock {
        override fun now(): Instant = instant
    }

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
        clock = clock,
    )

    private val vaultFolder = VaultFolder(state = state, folder = NoBackupFolder)

    /** One rung, and a folder that cannot be reached: nothing here changes destination. */
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
        archiveImport = ArchiveImport(
            state = state,
            destination = destination,
            verifier = verifier,
            files = files,
            clock = clock,
        ),
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

    /** A copy of the archive as it stands, taken the way the three triggers take one. */
    private suspend fun capture(): StoredBackup {
        live.transactionDao().insert(TransactionEntity(title = "coffee", date = DATE))
        instant += 1.minutes
        return assertIs<CaptureOutcome.Captured>(vault.captureIfNeeded()).copy
    }

    /** The copy taken before a migration, under the one name reserved for it. */
    private suspend fun captureFromUpdate(): StoredBackup {
        val captured = temporary("update")
        live.transactionDao().insert(TransactionEntity(title = "rent", date = DATE))
        live.captureInto(
            destinationPath = captured.absolutePath,
            appVersion = "1.2.3",
            platform = "desktop",
        )
        return assertNotNull(
            destination.put(captured.absolutePath, PRE_MIGRATION_BACKUP_NAME).getOrNull(),
            "the copy taken before an update did not land in the destination",
        )
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

    /** The screen, once it has finished reading the folder it opened over. */
    private suspend fun opened(): BackupHistoryViewModel = viewModel().also {
        it.await("the screen never finished its first listing") { ui -> !ui.isLoading }
    }

    // ----------------------------------------------------------- the question comes first

    /**
     * The whole of it. The delete row is a tap on a list, the file it names holds an archive
     * nobody can type again, and nothing here brings one back — so the tap asks, and the
     * folder is untouched until it is answered.
     */
    @Test
    fun `the delete row asks before anything leaves the folder`() = runTest {
        state.setOn(true)
        val copy = capture()
        val viewModel = opened()

        viewModel.onAction(BackupHistoryAction.Remove(copy))

        val asked = viewModel.await("the removal was never put to the person") {
            it.pendingRemoval != null
        }
        assertEquals(copy, asked.pendingRemoval, "the question is about the copy that was tapped")
        assertTrue(File(folder, copy.name).exists(), "the copy went before the question did")
        assertEquals(
            listOf(copy.name),
            assertNotNull(destination.list().getOrNull()).map { it.name },
            "the folder still holds the copy the question is about",
        )
    }

    /** Leaving the question unanswered is answering no, and the copy stays where it is. */
    @Test
    fun `a question answered by leaving keeps the copy`() = runTest {
        state.setOn(true)
        val copy = capture()
        val viewModel = opened()

        viewModel.onAction(BackupHistoryAction.Remove(copy))
        viewModel.await("the removal was never put to the person") { it.pendingRemoval != null }
        viewModel.onAction(BackupHistoryAction.AbandonRemoval)

        val after = viewModel.await("the question never came down") { it.pendingRemoval == null }
        assertFalse(after.isBusy, "nothing should have started")
        assertTrue(File(folder, copy.name).exists(), "a question that was declined took the copy")
        assertEquals(
            copy.asArchiveCopy(),
            state.observe().value.archiveCopy,
            "nothing was removed, so the vault was told nothing",
        )
    }

    /** And the answer that says yes is what actually removes it. */
    @Test
    fun `the copy goes once the question is answered`() = runTest {
        state.setOn(true)
        val copy = capture()
        val viewModel = opened()

        viewModel.onAction(BackupHistoryAction.Remove(copy))
        viewModel.await("the removal was never put to the person") { it.pendingRemoval != null }
        viewModel.onAction(BackupHistoryAction.ConfirmRemove)

        viewModel.await("the removal never finished") { ui ->
            !ui.isLoading && !ui.isBusy && ui.copies.none { it.name == copy.name }
        }
        assertFalse(File(folder, copy.name).exists(), "the copy is still in the folder")
        assertNull(
            state.observe().value.archiveCopy,
            "the copy that covered the archive was removed, and the vault was told",
        )
    }

    // ------------------------------------------------- the copy kept before an update

    /**
     * **It is removable by hand, and the confirmation is what makes that safe.**
     *
     * Retention is told never to sweep it (design D10) because the damage it exists to undo
     * is found out days later, and the app acting unattended must not carry it off. A person
     * in front of the screen is the other case entirely: the folder is one they can open with
     * a file manager, so a control the app refused would only send them to delete the same
     * file somewhere the app cannot say what it is. What is owed them is the sentence, and
     * the confirmation is where it stands.
     */
    @Test
    fun `the copy kept before an update is removed by hand, once it is confirmed`() = runTest {
        state.setOn(true)
        val fromUpdate = captureFromUpdate()
        val viewModel = opened()

        viewModel.await("the copy from the update was never listed") { ui ->
            ui.copies.any { it.name == PRE_MIGRATION_BACKUP_NAME }
        }
        viewModel.onAction(BackupHistoryAction.Remove(fromUpdate))
        val asked = viewModel.await("the removal was never put to the person") {
            it.pendingRemoval != null
        }

        assertEquals(
            PRE_MIGRATION_BACKUP_NAME,
            assertNotNull(asked.pendingRemoval).name,
            "the sheet has to be able to say which copy this is",
        )
        assertTrue(File(folder, PRE_MIGRATION_BACKUP_NAME).exists(), "it went before the answer")

        viewModel.onAction(BackupHistoryAction.ConfirmRemove)
        viewModel.await("the removal never finished") { ui ->
            !ui.isLoading && !ui.isBusy && ui.copies.none { it.name == PRE_MIGRATION_BACKUP_NAME }
        }

        assertFalse(
            File(folder, PRE_MIGRATION_BACKUP_NAME).exists(),
            "a copy the person confirmed the removal of is still in the folder",
        )
    }

    private companion object {
        val DATE = LocalDate.parse("2026-08-30")

        const val WAIT_MILLIS = 20_000L

        /**
         * A database is up to three files while something has it open in write-ahead
         * logging, and the verification opens a candidate with Room.
         */
        val DATABASE_FILES = listOf("", "-wal", "-shm", ".lck")
    }
}

/** The seeding with the device taken out of it: the seed, and the code as its own glyph. */
private fun removeCopySeeding() = object : CurrencySeeding {
    override fun rows(): List<SeedCurrency> = CURRENCY_SEED.map { SeedCurrency(it, it) }
    override fun symbolOf(code: String): String = code
}
