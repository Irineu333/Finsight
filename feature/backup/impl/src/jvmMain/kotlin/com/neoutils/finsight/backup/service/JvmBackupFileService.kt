package com.neoutils.finsight.backup.service

import arrow.core.Either
import arrow.core.right
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.backup_confirm_cancel
import com.neoutils.finsight.resources.backup_export_replace_action
import com.neoutils.finsight.resources.backup_export_replace_message
import com.neoutils.finsight.resources.backup_export_replace_title
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import com.neoutils.finsight.util.UiText
import java.awt.Component
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JFileChooser
import javax.swing.JOptionPane
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
                }.mapLeft { it.toBackupError(BackupError.VERIFICATION_FAILED) }
            }.onRight { unclaimed = null }
        } finally {
            unclaimed?.let { withContext(NonCancellable) { discard(it) } }
        }
    }

    /**
     * The save dialog picks a path, and a path the user picked may already hold a file of
     * theirs.
     *
     * `JFileChooser` warns about none of that: it approves a name that is taken exactly as
     * it approves a free one, and the copy that follows replaces whatever was there. So the
     * replacement is put to the person before it happens, and it is put as a statement of
     * what the save will do rather than as a question about a file — the file is theirs and
     * this app knows nothing about it beyond its name.
     *
     * Declining leaves the file alone and leaves as `false` — the same answer a closed save
     * dialog gives, because both are somebody deciding not to export and neither is a
     * failure anybody should be shown an error about.
     */
    override suspend fun copyOutCapturedFile(
        sourcePath: String,
        suggestedName: String,
        context: PlatformContext,
    ): Either<BackupError, Boolean> {
        val destination = context.chooseFile(
            suggestedName = suggestedName,
            show = JFileChooser::showSaveDialog,
        ) ?: return false.right()

        val taken = withContext(Dispatchers.IO) { destination.exists() }
        if (taken && !context.permitsReplacing(destination)) return false.right()

        return withContext(Dispatchers.IO) {
            Either.catch { File(sourcePath).copyTo(destination, overwrite = true) }
                .map { true }
                .mapLeft { it.toBackupError(BackupError.EXPORT_FAILED) }
        }
    }

    /**
     * Says what saving here would do to [file], and answers true only where the person
     * pressed the one button that says to do it.
     *
     * Every sentence on it is resolved before the event dispatch thread is reached, because
     * reading a string resource suspends and a dialog on the EDT cannot wait for anything.
     * The two buttons are named rather than left to `showConfirmDialog`: that one draws a
     * yes and a no out of the look-and-feel, which follows the JVM's locale and not the
     * language this app is being read in.
     *
     * They are laid out the way every confirmation in this app is — the way out on the
     * left, the destructive act on the right — and the way out is the one the dialog opens
     * on. Everything that is not that button, the window's own close included, leaves the
     * file alone.
     */
    private suspend fun PlatformContext.permitsReplacing(file: File): Boolean {
        val title = UiText.Res(Res.string.backup_export_replace_title).asString()
        val message =
            UiText.ResWithArgs(Res.string.backup_export_replace_message, file.name).asString()
        val cancel = UiText.Res(Res.string.backup_confirm_cancel).asString()
        val replace = UiText.Res(Res.string.backup_export_replace_action).asString()

        return suspendCancellableCoroutine { continuation ->
            SwingUtilities.invokeLater {
                val answer = JOptionPane.showOptionDialog(
                    windowScope.window,
                    message,
                    title,
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    arrayOf(cancel, replace),
                    cancel,
                )
                continuation.resume(answer == REPLACE_OPTION)
            }
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
internal fun Throwable.toBackupError(otherwise: BackupError): BackupError =
    if (isOutOfSpace()) BackupError.NO_SPACE else otherwise

private fun Throwable.isOutOfSpace(): Boolean = generateSequence(this) { it.cause }
    .take(MAX_CAUSE_DEPTH)
    .any { cause -> cause is IOException && OUT_OF_SPACE.any { it in cause.message.orEmpty() } }

/**
 * Everything that appears beside a database file on this platform once something opens it,
 * and a candidate is opened with Room.
 *
 * Three of them are SQLite's own: the database, the write-ahead log and its shared-memory
 * index. The fourth is Room's, and it is the one that matters outside this app's own
 * areas — on the JVM, Room takes a `.lck` file next to the database it opens and leaves it
 * there afterwards. Removing the main file alone would leave a `finsight-backup-….db.lck`
 * standing in the folder the user chose, for a copy that is no longer in it: litter in
 * somebody's own documents, from a vault whose whole promise is to keep to a folder of its
 * own (design D4).
 */
internal val DATABASE_FILES = listOf("", "-wal", "-shm", ".lck")

/**
 * What a database opening leaves *beside* the file it opened. It is what has to go when a
 * copy is replaced rather than removed: the main file is overwritten by the replacement, and
 * these belong to the copy that was there before it.
 */
internal val JOURNAL_FILES = DATABASE_FILES.drop(1)

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
/**
 * Where the button that replaces the file sits in the pair the save warning offers — second,
 * because the way out is offered first, and [JOptionPane.showOptionDialog] answers with the
 * index of what was pressed.
 */
private const val REPLACE_OPTION = 1

private const val CANDIDATE_PREFIX = "candidate-"
private const val CANDIDATE_SUFFIX = ".db"
private const val MAX_CAUSE_DEPTH = 8
private val OUT_OF_SPACE = listOf("ENOSPC", "No space left on device")
