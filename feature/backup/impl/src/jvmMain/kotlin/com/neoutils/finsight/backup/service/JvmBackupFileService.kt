package com.neoutils.finsight.backup.service

import arrow.core.Either
import arrow.core.right
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import java.awt.Component
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * A Swing file chooser, on the one platform where what the user picks is already a path.
 *
 * The copy on the way in is not there to turn a handle into a file, as it is on the other
 * two, but because the verification runs the migration chain over what it is handed, and
 * the user's own file is not something to run it over.
 */
class JvmBackupFileService : BackupFileService {

    override suspend fun copyInChosenFile(
        context: PlatformContext,
    ): Either<BackupError, String?> {
        val chosen = context.chooseFile(
            suggestedName = null,
            show = JFileChooser::showOpenDialog,
        ) ?: return null.right()

        return copyIntoPrivateFile(chosen)
    }

    /**
     * A private copy of [chosen], at a path this app may write to and lose.
     *
     * The copy is removed unless the path is handed back, and it is a `finally` rather than
     * a failure path because the way it is most easily lost is not a failure: the copy runs
     * to the end whatever the caller's scope is doing, and [withContext] then raises the
     * cancellation instead of returning — the file exists and nobody has been told where.
     * A caller cannot close what it was never given, and the path is minted here.
     */
    internal suspend fun copyIntoPrivateFile(chosen: File): Either<BackupError, String> {
        var unclaimed: String? = null
        try {
            return withContext(Dispatchers.IO) {
                Either.catch {
                    val destination = createPrivateFile()
                    unclaimed = destination.absolutePath
                    chosen.copyTo(destination, overwrite = true)
                    destination.absolutePath
                }.mapLeft { it.toBackupError(BackupError.NOT_A_BACKUP) }
            }.onRight { unclaimed = null }
        } finally {
            unclaimed?.let { withContext(NonCancellable) { discard(it) } }
        }
    }

    override suspend fun copyOutCapturedFile(
        sourcePath: String,
        suggestedName: String,
        context: PlatformContext,
    ): Either<BackupError, Boolean> {
        val destination = context.chooseFile(
            suggestedName = suggestedName,
            show = JFileChooser::showSaveDialog,
        ) ?: return false.right()

        return withContext(Dispatchers.IO) {
            Either.catch { File(sourcePath).copyTo(destination, overwrite = true) }
                .map { true }
                .mapLeft { it.toBackupError(BackupError.EXPORT_FAILED) }
        }
    }

    override suspend fun newCapturePath(): Either<BackupError, String> =
        withContext(Dispatchers.IO) {
            Either.catch { createPrivateFile().apply { delete() }.absolutePath }
                .mapLeft { it.toBackupError(BackupError.EXPORT_FAILED) }
        }

    override suspend fun discard(path: String) {
        withContext(Dispatchers.IO) {
            DATABASE_FILES.forEach { suffix -> File(path + suffix).delete() }
        }
    }

    /**
     * Runs a chooser on the event dispatch thread and suspends until it closes, answering
     * null when it closes without an approval.
     *
     * No file filter is installed, on either dialog. Restricting the open dialog to one
     * extension hides backups this app reads perfectly well — what a file is gets settled
     * by reading it — and the save dialog needs none: the name it is handed already carries
     * the extension.
     */
    private suspend fun PlatformContext.chooseFile(
        suggestedName: String?,
        show: (JFileChooser, Component) -> Int,
    ): File? = suspendCancellableCoroutine { continuation ->
        SwingUtilities.invokeLater {
            val chooser = JFileChooser()
            if (suggestedName != null) {
                chooser.selectedFile = File(suggestedName)
            }
            val outcome = show(chooser, windowScope.window)
            continuation.resume(
                if (outcome == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
            )
        }
    }

    private fun createPrivateFile(): File =
        Files.createTempFile(privateDirectory, CANDIDATE_PREFIX, CANDIDATE_SUFFIX).toFile()
}

/**
 * A full disk is the one failure the user can act on, so it is the one told apart. The
 * message is what carries it: a write that runs out of room surfaces as an [IOException]
 * naming the condition, either on its own or wrapped by whatever was copying at the time.
 */
private fun Throwable.toBackupError(otherwise: BackupError): BackupError =
    if (isOutOfSpace()) BackupError.NO_SPACE else otherwise

private fun Throwable.isOutOfSpace(): Boolean = generateSequence(this) { it.cause }
    .take(MAX_CAUSE_DEPTH)
    .any { cause -> cause is IOException && OUT_OF_SPACE.any { it in cause.message.orEmpty() } }

/**
 * A database is up to three files while it is open in write-ahead logging, and a
 * candidate is opened with Room. Removing the main file alone would leave the other two
 * behind for as long as the temporary directory lives.
 */
private val DATABASE_FILES = listOf("", "-wal", "-shm")

/**
 * A candidate is opened with Room and migrated in place before anything is decided
 * about it, and a capture only lives until it has been handed over, so both live where
 * the system throws files away rather than beside the database one of them might come
 * to replace.
 *
 * The directory is what keeps the archive to this user, and the only thing that can:
 * on Linux `java.io.tmpdir` is `/tmp`, shared by every account on the machine, and
 * both flows destroy and recreate the file they are handed a path to — the capture
 * through `VACUUM INTO`, the restore through an overwriting copy — so a mode set on
 * the file itself is gone before it holds a byte. It is also the only cover the `-wal`
 * and `-shm` files Room leaves beside a candidate ever get, since this service does
 * not create them.
 *
 * The name is drawn rather than fixed, because `mkdirs` accepts a directory another
 * local user made first, with whatever permissions or symlink they chose;
 * [Files.createTempDirectory] refuses a path that exists. Made on first use, so a run
 * that never opens the backup screen leaves nothing behind, and made once for the run
 * rather than once per service, so that opening the screen twice does not strand an
 * empty directory each time. It is asked to go on exit, which it can be, because
 * everything put inside it is removed as it is finished with.
 *
 * No [java.nio.file.attribute.FileAttribute] is passed: the JDK already applies
 * `rwx------` and `rw-------` where the filesystem has POSIX modes and skips them
 * where it has none, while an explicit permission attribute would throw on Windows.
 */
private val privateDirectory: Path by lazy {
    Files.createTempDirectory(PRIVATE_DIRECTORY).also { it.toFile().deleteOnExit() }
}

/** The prefix of the drawn directory name, not a name of its own. */
private const val PRIVATE_DIRECTORY = "finsight-backup"
private const val CANDIDATE_PREFIX = "candidate-"
private const val CANDIDATE_SUFFIX = ".db"
private const val MAX_CAUSE_DEPTH = 8
private val OUT_OF_SPACE = listOf("ENOSPC", "No space left on device")
