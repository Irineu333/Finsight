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
 *
 * **One more path, beside the one [point] writes.** [pointAt] shifts whatever [KEY_FOLDER]
 * held into [KEY_FOLDER_PREVIOUS] the instant it is about to be overwritten by a genuinely
 * different path — never on a first-ever pointing, and never on a re-point at the path
 * already remembered (task 11.10). [previous] reads that second key with everything else
 * this class already knows how to do, which is what lets a carry offered right after a
 * folder change still read the folder being left, through
 * [com.neoutils.finsight.domain.vault.VaultDestinations.rungFor] — even though the app's one
 * *current* path has already moved on to naming the new folder by the time the offer is
 * answered.
 */
class JvmBackupFolder private constructor(
    private val settings: Settings,
    /**
     * How a folder is put to the person — the platform's dialog by default, and the only
     * part of pointing at one that cannot be exercised anywhere. See
     * [chooseDirectoryWithSwing].
     */
    private val choose: suspend (PlatformContext) -> File?,
    /** Which settings key this instance reads and writes — [KEY_FOLDER] or [KEY_FOLDER_PREVIOUS]. */
    private val key: String,
) : BackupFolder {

    constructor(
        settings: Settings,
        choose: suspend (PlatformContext) -> File? = { chooseDirectoryWithSwing(it) },
    ) : this(settings, choose, KEY_FOLDER)

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
                shiftToPreviousIfChanged(chosen)
                // Written last: a preference naming a folder this app could not read would
                // be a vault pointed somewhere it cannot write.
                settings.putString(key, chosen.absolutePath)
                true
            }.mapLeft { it.toBackupError(BackupError.EXPORT_FAILED) }
        }
    }

    /**
     * Keeps the path [pointAt] is about to overwrite reachable under [KEY_FOLDER_PREVIOUS]
     * (task 11.10). It runs only on the instance that owns [KEY_FOLDER] — the previous-token
     * reader's own [pointAt] is never actually called — and it shifts nothing when there was
     * no path remembered yet, or when the path chosen is the one already remembered: a
     * first-ever pointing and a re-point at the same folder both touch nothing.
     */
    private fun shiftToPreviousIfChanged(chosen: File) {
        if (key != KEY_FOLDER) return
        val before = settings.getStringOrNull(key)
        if (before != null && before != chosen.absolutePath) {
            settings.putString(KEY_FOLDER_PREVIOUS, before)
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
     * [key] untouched: a path never rewrites itself, so a folder chosen twice reads the same
     * text both times.
     */
    override val identity: FolderIdentity?
        get() = settings.getStringOrNull(key)?.let(::folderIdentity)

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
    internal fun chosenFolder(): File? = settings.getStringOrNull(key)?.let(::File)

    /** [BackupFolder.forgetPrevious] — see the class comment for the two occasions this runs. */
    override fun forgetPrevious() = settings.remove(KEY_FOLDER_PREVIOUS)

    companion object {

        /**
         * The desktop's own key. Each platform remembers its own kind of token — a path
         * here, a tree `Uri` on Android, a bookmark on iOS — and no install ever reads
         * another platform's.
         */
        private const val KEY_FOLDER = "backup_vault_folder"

        /**
         * The path [pointAt] shifted aside on its last change, beside [KEY_FOLDER] rather
         * than instead of it — both are held at once so a carry offered right after a folder
         * change can still read the one being left (task 11.10).
         */
        private const val KEY_FOLDER_PREVIOUS = "backup_vault_folder_previous"

        /**
         * A read-only reader of the path [pointAt] most recently shifted aside — everything
         * [JvmBackupFolder] already knows how to do, over [KEY_FOLDER_PREVIOUS] instead of
         * [KEY_FOLDER] (task 11.10). Its own [point]/[pointAt] are never meant to be called —
         * [choose] answers null unconditionally, so a call resolves to *nothing chosen*
         * rather than doing anything to either key.
         */
        fun previous(settings: Settings): JvmBackupFolder =
            JvmBackupFolder(settings, choose = { null }, key = KEY_FOLDER_PREVIOUS)
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
