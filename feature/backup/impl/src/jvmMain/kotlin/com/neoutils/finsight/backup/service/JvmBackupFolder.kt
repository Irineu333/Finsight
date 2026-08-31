package com.neoutils.finsight.backup.service

import arrow.core.Either
import arrow.core.right
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.ui.screen.backup.service.BACKUP_FOLDER_NAME
import com.neoutils.finsight.ui.screen.backup.service.BackupFolder
import com.neoutils.finsight.ui.screen.backup.service.FolderLink
import com.russhwolf.settings.Settings
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
 * The desktop's half of design D4's machine: a Swing chooser set to directories, and a path
 * remembered in the app's preferences.
 *
 * **Without ceremony, and that is the whole of the desktop** (task 11.6). There is no
 * permission to persist, no bookmark to resolve and nothing to revoke: what the person
 * points at is a path, it stays a path, and it works after a restart because a path is what
 * the file system answers to. It is the reason the folder rung is built here first — every
 * rule around the folder can be settled and tested before Android's tree `Uri` and iOS's
 * security-scoped bookmark are written against the same contract.
 *
 * **The path never leaves this module** (design D2). [BackupFolder] answers three words
 * about the link and nothing else; the one member that hands out a [File] is `internal`,
 * visible to [JvmFolderBackupDestination] beside it and to nothing in common code. That is
 * the compiler holding the rule rather than a comment asking for it.
 *
 * **Only this creates the app's own subfolder, and only with a person in front of the
 * screen.** Nothing on the writing path ever makes it (design D9): a mountpoint that is
 * still a directory after the volume behind it has gone answers every question the way an
 * empty folder does, and a subfolder rebuilt into it would take the copies while the real
 * archive sits on a disk that is no longer attached. Rebuilding it is therefore an act
 * somebody performs, through the same picker they chose the folder with.
 */
class JvmBackupFolder(
    private val settings: Settings,
    /**
     * How a folder is put to the person — the platform's dialog by default, and the only
     * part of pointing at one that cannot be exercised anywhere. See
     * [chooseDirectoryWithSwing].
     */
    private val choose: suspend (PlatformContext) -> File? = { chooseDirectoryWithSwing(it) },
) : BackupFolder {

    override val isOffered = true

    override suspend fun point(context: PlatformContext): Either<BackupError, Boolean> =
        pointAt(choose(context))

    /**
     * Everything pointing at a folder means, once one has been pointed at.
     *
     * It is apart from [point] because the dialog is the only half that needs a person in
     * front of the screen, and every rule is in this half: what null means, which folder is
     * made, and when the preference is written. Splitting them is also what makes those
     * rules provable without a window — a folder chooser cannot be driven by a test on any
     * platform, and the rules are what would break.
     */
    internal suspend fun pointAt(chosen: File?): Either<BackupError, Boolean> {
        if (chosen == null) return false.right()

        return withContext(Dispatchers.IO) {
            Either.catch {
                val own = File(chosen, BACKUP_FOLDER_NAME)
                if (!own.isDirectory && !own.mkdirs()) {
                    throw IOException("The folder for backups could not be made")
                }
                // Written last: a preference naming a folder this app could not prepare
                // would be a vault pointed somewhere it cannot write.
                settings.putString(KEY_FOLDER, chosen.absolutePath)
                true
            }.mapLeft { it.toBackupError(BackupError.EXPORT_FAILED) }
        }
    }

    /**
     * The link is the app's own subfolder being there, not the chosen folder being there.
     *
     * The stricter of the two readings is the right one, and for the reason above: a
     * chosen folder that answers *yes, a directory* while the subfolder inside it has gone
     * is the shape a detached volume takes, and it is also the shape of somebody having
     * deleted the copies. Both are a link that has fallen, and neither is something to
     * repair without asking.
     */
    override suspend fun link(): FolderLink = withContext(Dispatchers.IO) {
        val own = ownFolder() ?: return@withContext FolderLink.NONE
        if (own.isDirectory) FolderLink.LINKED else FolderLink.BROKEN
    }

    /**
     * The folder the copies go in — the app's own inside the one that was chosen — or null
     * when nothing has been pointed at.
     *
     * `internal` and answering a [File] is the one concession to the platform, and it goes
     * no further than this module: design D2 is about what a *caller* of the destination
     * can learn, and what it can learn stays [FolderLink].
     */
    internal fun ownFolder(): File? =
        settings.getStringOrNull(KEY_FOLDER)?.let { File(File(it), BACKUP_FOLDER_NAME) }

    private companion object {

        /**
         * The desktop's own key. Each platform remembers its own kind of token — a path
         * here, a tree `Uri` on Android, a bookmark on iOS — and no install ever reads
         * another platform's.
         */
        const val KEY_FOLDER = "backup_vault_folder"
    }
}

/**
 * Runs a chooser set to directories on the event dispatch thread and suspends until it
 * closes, answering null when it closes without an approval.
 *
 * It is a function beside the class rather than a method on it because it is the one half
 * of pointing at a folder that needs a person in front of the screen, and the half that
 * cannot be exercised anywhere: no test on any platform drives a system file dialog. What
 * a chosen folder *means* is [JvmBackupFolder.pointAt], and that is where the rules are.
 *
 * It is not [JvmBackupFileService]'s chooser under another name. That one carries a file
 * the user hands over or takes away, in a dialog that has to name it; this one picks a
 * place, shows no file at all, and its answer is remembered rather than read. Sharing the
 * dozen lines of event-thread plumbing between them would join two dialogs that have
 * neither a mode nor a purpose in common.
 */
internal suspend fun chooseDirectoryWithSwing(context: PlatformContext): File? =
    suspendCancellableCoroutine { continuation ->
        SwingUtilities.invokeLater {
            val chooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                isMultiSelectionEnabled = false
            }
            val outcome: Int = chooser.showOpenDialog(context.windowScope.window as Component)
            continuation.resume(
                if (outcome == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
            )
        }
    }
