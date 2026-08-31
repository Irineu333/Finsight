package com.neoutils.finsight.backup.service

import arrow.core.Either
import arrow.core.right
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.ui.screen.backup.service.BackupFolder
import com.neoutils.finsight.ui.screen.backup.service.FolderIdentity
import com.neoutils.finsight.ui.screen.backup.service.FolderLink
import com.neoutils.finsight.ui.screen.backup.service.folderIdentity
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
 * remembered in the app's preferences. The copies go straight into that path — there is no
 * subfolder of the app's own inside it.
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
 * **A mountpoint left standing by a detached volume reads as a live, empty directory, and
 * nothing here can tell the two apart.** `File.isDirectory` is the only signal a plain path
 * gives, and a network share that has gone answers it exactly as a folder that is genuinely
 * still empty does. Keeping a self-made subfolder as a marker used to be what told them
 * apart; writing straight into the chosen path gives that up, so a capture that lands on a
 * stale mountpoint is not caught here.
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
     * front of the screen, and every rule is in this half: what null means and when the
     * preference is written. Splitting them is also what makes those rules provable without
     * a window — a folder chooser cannot be driven by a test on any platform, and the rules
     * are what would break.
     */
    internal suspend fun pointAt(chosen: File?): Either<BackupError, Boolean> {
        if (chosen == null) return false.right()

        return withContext(Dispatchers.IO) {
            Either.catch {
                if (!chosen.isDirectory) {
                    throw IOException("The chosen folder is not a directory")
                }
                // Written last: a preference naming a folder this app could not read would
                // be a vault pointed somewhere it cannot write.
                settings.putString(KEY_FOLDER, chosen.absolutePath)
                true
            }.mapLeft { it.toBackupError(BackupError.EXPORT_FAILED) }
        }
    }

    /**
     * The link is the chosen folder being there, read fresh every time.
     *
     * A path that no longer answers *yes, a directory* is the shape a deleted or detached
     * folder takes, and it is not repaired without asking (design D12).
     */
    override suspend fun link(): FolderLink = withContext(Dispatchers.IO) {
        val chosen = chosenFolder() ?: return@withContext FolderLink.NONE
        if (chosen.isDirectory) FolderLink.LINKED else FolderLink.BROKEN
    }

    /**
     * The chosen path's own text, fingerprinted — never the path itself, which stays
     * `internal` to this module (design D2). It is stable across everything that leaves
     * [KEY_FOLDER] untouched: a path never rewrites itself, so a folder chosen twice reads
     * the same text both times.
     */
    override val identity: FolderIdentity?
        get() = settings.getStringOrNull(KEY_FOLDER)?.let(::folderIdentity)

    /**
     * The chosen path's own last segment — never the path itself, which stays `internal` to
     * this module (design D2). Unlike [identity] this asks nothing of the file system: a
     * path's name is a property of its text, so a folder that has since been renamed or
     * removed still answers the name it was chosen under.
     */
    override suspend fun displayName(): String? =
        chosenFolder()?.name?.takeIf { it.isNotBlank() }

    /**
     * The folder the copies go in, or null when nothing has been pointed at.
     *
     * `internal` and answering a [File] is the one concession to the platform, and it goes
     * no further than this module: design D2 is about what a *caller* of the destination
     * can learn, and what it can learn stays [FolderLink].
     */
    internal fun chosenFolder(): File? = settings.getStringOrNull(KEY_FOLDER)?.let(::File)

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
