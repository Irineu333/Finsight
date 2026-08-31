@file:OptIn(ExperimentalTime::class)

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
import com.neoutils.finsight.domain.restore.RestoreConfirmation
import com.neoutils.finsight.domain.restore.RestoreOutcome
import com.neoutils.finsight.domain.restore.RestoreQuestions
import com.neoutils.finsight.domain.vault.BackupRetention
import com.neoutils.finsight.domain.vault.BackupVault
import com.neoutils.finsight.domain.vault.CaptureOutcome
import com.neoutils.finsight.domain.vault.VaultAppOpening
import com.neoutils.finsight.domain.vault.VaultDestination
import com.neoutils.finsight.domain.vault.VaultDestinations
import com.neoutils.finsight.domain.vault.VaultFolder
import com.neoutils.finsight.domain.vault.VaultPeriodicBackup
import com.neoutils.finsight.domain.vault.VaultPreventiveBackup
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.ui.screen.backup.service.BACKUP_FOLDER_NAME
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import com.neoutils.finsight.ui.screen.backup.service.FolderLink
import com.neoutils.finsight.ui.screen.backup.service.OwnCopyCheck
import com.neoutils.finsight.util.UiText
import com.russhwolf.settings.MapSettings
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
 * The second rung, end to end on the desktop: somebody points at a folder, and from that
 * moment the vault is a vault in *their* folder.
 *
 * Everything here is real — a Room archive, a folder on disk, the gate that reads a copy
 * before removing or restoring it — because every claim being made is about one of those
 * and not about this code. What a capture does to a folder, what a listing of a folder that
 * has gone answers, and whether a preference read back after a restart still names the same
 * place are all facts about the file system, and a fake asked any of them would answer
 * whatever the test wanted.
 *
 * The one thing standing in for the real article is the folder chooser, which no test on
 * any platform can drive. What it would have returned is handed to the same call the dialog
 * feeds, so the whole of the machine below it runs.
 */
class FolderVaultTest {

    private val temporaries = mutableListOf<File>()

    private val chosen: File = Files.createTempDirectory("finsight-user-folder").toFile()

    private val appStorageFolder: File =
        Files.createTempDirectory("finsight-app-storage").toFile()

    private fun temporary(name: String): File =
        File.createTempFile("finsight-e2e-$name", ".db")
            .also { it.delete(); temporaries += it }

    private fun roomAt(path: String): AppDatabase = getRoomDatabase(
        builder = getDatabaseBuilder(path = path),
        baseCurrency = "BRL",
        currencySeeding = e2eSeeding(),
    )

    private val live = roomAt(temporary("live").absolutePath)

    private val settings = MapSettings()

    private val state = BackupVaultRepository(settings)

    private var instant: Instant = Instant.parse("2026-08-30T10:00:00Z")

    private val verifier = CandidateVerifier(::roomAt)

    private val ownCopy = OwnCopyCheck(verifier)

    private val backupFolder = JvmBackupFolder(settings) { chosen }

    private val destination = VaultDestinations(
        state = state,
        appStorage = JvmBackupDestination(ownCopy = ownCopy, directory = appStorageFolder),
        folder = JvmFolderBackupDestination(folder = backupFolder, ownCopy = ownCopy),
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

    private val folder = VaultFolder(state = state, folder = backupFolder)

    private val context = PlatformContext(
        object : WindowScope {
            override val window: ComposeWindow get() = error("no picker is raised here")
        }
    )

    private val own get() = File(chosen, BACKUP_FOLDER_NAME)

    @AfterTest
    fun tearDown() {
        live.close()
        temporaries.forEach { file ->
            DATABASE_FILES.forEach { File(file.absolutePath + it).delete() }
        }
        chosen.deleteRecursively()
        appStorageFolder.deleteRecursively()
    }

    // ------------------------------------------------------------------ the situation

    /** What the user entering something looks like from here. */
    private suspend fun enter(title: String): Long =
        live.transactionDao().insert(TransactionEntity(title = title, date = DATE))

    private suspend fun pointAtFolder() {
        assertEquals(true, folder.pointAt(context).getOrNull(), "the folder was not taken")
    }

    /** One occasion on which a trigger asks the vault for a copy. */
    private suspend fun asked(): CaptureOutcome {
        instant += 1.minutes
        return vault.captureIfNeeded()
    }

    private suspend fun captureSomethingNew(title: String): String {
        enter(title)
        return assertIs<CaptureOutcome.Captured>(asked()).copy.name
    }

    private fun namesInFolder(): List<String> =
        own.listFiles().orEmpty().map { it.name }.sorted()

    // ---------------------------------------------------------------- the copies land

    @Test
    fun `a capture lands in the folder the person chose, and not inside the app`() = runTest {
        pointAtFolder()
        state.setOn(true)

        val name = captureSomethingNew("coffee")

        assertEquals(listOf(name), namesInFolder())
        assertEquals(
            emptyList(),
            appStorageFolder.listFiles().orEmpty().map { it.name },
            "a copy went inside the app while the vault was pointed at a folder",
        )
    }

    @Test
    fun `the history is what the folder holds`() = runTest {
        pointAtFolder()
        state.setOn(true)
        val first = captureSomethingNew("coffee")
        val second = captureSomethingNew("bread")

        val listed = assertNotNull(destination.list().getOrNull()).map { it.name }

        assertEquals(setOf(first, second), listed.toSet())
        assertEquals(namesInFolder(), listed.sorted(), "the listing is a reading of the folder")
    }

    /**
     * A copy taken out of the folder by hand is simply not listed, and nothing is made of
     * it: the folder is one the person can also reach with a file manager (design D9).
     */
    @Test
    fun `a copy deleted from outside the app stops being listed, without an error`() = runTest {
        pointAtFolder()
        state.setOn(true)
        val name = captureSomethingNew("coffee")

        File(own, name).delete()

        assertEquals(emptyList(), assertNotNull(destination.list().getOrNull()))
    }

    /**
     * The limit is in force wherever the vault writes, and a folder somebody chose is
     * where they will see what is left behind: a copy that has been swept leaves no
     * `-wal`, no `-shm` and no `.lck` standing in their own folder.
     */
    @Test
    fun `retention sweeps the chosen folder and leaves nothing behind`() = runTest {
        pointAtFolder()
        state.setOn(true)
        state.setRetention(BackupRetention.FIVE)

        List(SIX) { captureSomethingNew("entry $it") }

        assertEquals(
            FIVE,
            namesInFolder().size,
            "the folder holds something other than the five copies kept: " + namesInFolder(),
        )
    }

    /**
     * A folder the person chose is a folder with their own things in it, and retention
     * confirms by content before it removes anything.
     */
    @Test
    fun `retention never touches a file the app did not write`() = runTest {
        pointAtFolder()
        state.setOn(true)
        state.setRetention(BackupRetention.FIVE)
        val theirs = File(own, "finsight-backup-notes.db")
            .apply { writeText("their own file, sitting in their own folder") }

        List(SIX) { captureSomethingNew("entry $it") }

        assertTrue(theirs.exists(), "the sweep removed a file this app never wrote")
    }

    // ------------------------------------------------------------------- and come back

    @Test
    fun `the archive is restored from a copy in the chosen folder`() = runTest {
        pointAtFolder()
        state.setOn(true)
        val id = enter("coffee")
        assertIs<CaptureOutcome.Captured>(asked())
        live.transactionDao().deleteById(id)
        assertEquals(null, live.transactionDao().getById(id), "it is gone before the restore")

        val copy = assertNotNull(destination.list().getOrNull()).first()
        val outcome = ArchiveRestore(
            database = live,
            verifier = verifier,
            preventive = VaultPreventiveBackup(state, vault),
            vault = vault,
            files = files,
        ).restoreFrom(
            candidate = {
                val path = temporary("candidate").absolutePath
                destination.copyOut(copy, path).map { copied -> path.takeIf { copied } }
            },
            questions = alwaysYes,
            from = copy,
        )

        assertEquals(RestoreOutcome.Restored, outcome)
        assertNotNull(
            live.transactionDao().getById(id),
            "the row came back from the copy in the folder",
        )
    }

    // ---------------------------------------------------------------- across a restart

    /**
     * The desktop's whole promise (task 11.6). Nothing is renewed, resolved or re-granted:
     * a second set of objects over the same preferences finds the same folder and goes on
     * writing into it.
     */
    @Test
    fun `the folder is still the destination after the app is started again`() = runTest {
        pointAtFolder()
        state.setOn(true)
        val before = captureSomethingNew("coffee")

        val restartedState = BackupVaultRepository(settings)
        val restartedFolder = JvmBackupFolder(settings) { chosen }
        val restartedDestination = VaultDestinations(
            state = restartedState,
            appStorage = JvmBackupDestination(ownCopy = ownCopy, directory = appStorageFolder),
            folder = JvmFolderBackupDestination(folder = restartedFolder, ownCopy = ownCopy),
        )

        assertEquals(VaultDestination.USER_FOLDER, restartedState.observe().value.destination)
        assertEquals(FolderLink.LINKED, restartedFolder.link())
        assertEquals(
            listOf(before),
            assertNotNull(restartedDestination.list().getOrNull()).map { it.name },
            "the copies taken before the restart were not found again",
        )
    }

    // ------------------------------------------------- and when the folder is not there

    /**
     * Task 11.7. The folder is gone and the periodic trigger is switched off, so nothing
     * was going to write anything — and the app still notices when it opens, which is the
     * whole difference between finding out now and finding out days later through a copy
     * that quietly stopped being taken (design D12).
     */
    @Test
    fun `a folder that has gone is noticed when the app opens, not when something is written`() =
        runTest {
            pointAtFolder()
            state.setOn(true)
            captureSomethingNew("coffee")
            state.setPeriodicOn(false)
            chosen.deleteRecursively()

            VaultAppOpening(
                folder = folder,
                periodic = VaultPeriodicBackup(
                    state = state,
                    vault = vault,
                    clock = object : Clock {
                        override fun now(): Instant = instant
                    },
                ),
            ).captureIfDue()

            assertEquals(FolderLink.BROKEN, folder.link.value)
            assertFalse(chosen.exists(), "nothing rebuilt the folder that had gone")
        }

    @Test
    fun `a capture into a folder that has gone fails and rebuilds nothing`() = runTest {
        pointAtFolder()
        state.setOn(true)
        chosen.deleteRecursively()

        enter("coffee")
        instant += 1.minutes

        assertIs<CaptureOutcome.Failed>(vault.captureIfNeeded())
        assertFalse(chosen.exists(), "the app rebuilt a folder somebody had taken away")
    }

    private val alwaysYes = object : RestoreQuestions {
        override suspend fun confirm(confirmation: RestoreConfirmation) = true
        override suspend fun permitWithoutCopy(reason: UiText) = true
    }

    private companion object {
        val DATE = LocalDate(2026, 8, 30)

        const val FIVE = 5
        const val SIX = 6

        val DATABASE_FILES = listOf("", "-wal", "-shm")
    }
}

/** The seeding with the device taken out of it: the seed, and the code as its own glyph. */
private fun e2eSeeding() = object : CurrencySeeding {
    override fun rows(): List<SeedCurrency> = CURRENCY_SEED.map { SeedCurrency(it, it) }
    override fun symbolOf(code: String): String = code
}
