package com.neoutils.finsight.backup.service

import arrow.core.Either
import arrow.core.right
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import java.awt.Component
import java.io.File
import java.io.IOException
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
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

        return withContext(Dispatchers.IO) {
            Either.catch {
                val destination = createPrivateFile()
                chosen.copyTo(destination, overwrite = true)
                destination.absolutePath
            }.mapLeft { it.toBackupError(BackupError.NOT_A_BACKUP) }
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

    /**
     * A candidate is opened with Room and migrated in place before anything is decided
     * about it, so it lives where the system throws files away rather than beside the
     * database it might come to replace.
     */
    private fun createPrivateFile(): File {
        val directory = File(System.getProperty("java.io.tmpdir"), PRIVATE_DIRECTORY)
            .apply { mkdirs() }
        return File.createTempFile(CANDIDATE_PREFIX, CANDIDATE_SUFFIX, directory)
    }
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

private const val PRIVATE_DIRECTORY = "finsight-backup"
private const val CANDIDATE_PREFIX = "candidate-"
private const val CANDIDATE_SUFFIX = ".db"
private const val MAX_CAUSE_DEPTH = 8
private val OUT_OF_SPACE = listOf("ENOSPC", "No space left on device")
