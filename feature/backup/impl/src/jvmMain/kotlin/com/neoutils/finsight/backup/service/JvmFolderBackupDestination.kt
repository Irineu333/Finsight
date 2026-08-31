package com.neoutils.finsight.backup.service

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.ui.screen.backup.service.BackupDestination
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import com.neoutils.finsight.ui.screen.backup.service.NEWEST_FIRST
import com.neoutils.finsight.ui.screen.backup.service.OwnCopyCheck
import com.neoutils.finsight.ui.screen.backup.service.StoredBackup
import com.neoutils.finsight.ui.screen.backup.service.freeBackupFileName
import com.neoutils.finsight.ui.screen.backup.service.isBackupFileName
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * The second rung on the desktop: the same four operations as [JvmBackupDestination], over
 * the folder somebody pointed at instead of the app's own. The copies go straight into it —
 * there is no subfolder of the app's own inside it.
 *
 * **It is a class of its own and not the first rung with a different directory**, because
 * the two disagree about the one thing that matters, which is what *absence* means. The
 * app's own folder is absent because nothing has been captured yet, and answering an empty
 * list is the truth. A folder somebody chose is absent because a volume was detached, a
 * folder was renamed or a sync client removed it — and answering an empty list there is
 * design D9's forbidden sentence: **zero copies means "could not read", never "there is
 * nothing here"**. Parameterising one class over that difference would hide the only rule
 * in this file worth reading.
 *
 * **Nothing here creates a directory.** A folder that is not there refuses rather than being
 * rebuilt — see [JvmBackupFolder]'s own comment for the one case this cannot catch: a
 * mountpoint a detached volume left standing reads as a live, empty directory, no
 * differently from a folder that is genuinely still empty.
 *
 * **The copies in the folder are read and never written to.** Retention confirms by content
 * before removing anything ([OwnCopyCheck]), so a folder holding the user's own files loses
 * none of them — and the confirmation itself is run over a copy in the app's own area, never
 * over the file in the folder. See [remove].
 */
class JvmFolderBackupDestination(
    private val folder: JvmBackupFolder,
    private val ownCopy: OwnCopyCheck,
    /**
     * Where a copy is written to be read. The gate that proves a file is this app's migrates
     * what it is handed, so what it is handed must be a copy that may be lost — which a file
     * sitting in somebody's own folder is not. It comes into the app's own temporary area
     * through the service that already owns that decision and already knows every file a
     * database opening leaves behind.
     */
    private val files: BackupFileService,
) : BackupDestination {

    override suspend fun put(
        capturedPath: String,
        name: String,
    ): Either<BackupError, StoredBackup> = withContext(Dispatchers.IO) {
        reachable().flatMap { directory ->
            Either.catch {
                val free = freeBackupFileName(name) { File(directory, it).exists() }
                copyIntoPlace(File(capturedPath), File(directory, free)).asStoredBackup()
            }.mapLeft { it.toBackupError(BackupError.EXPORT_FAILED) }
        }
    }

    /**
     * What the folder holds, or a refusal — and a refusal covers every way the answer could
     * be incomplete, `listFiles` answering null included.
     *
     * `File.listFiles` returns null both for a path that is not a directory and for a
     * directory it could not read, and the two are indistinguishable from here. Turning
     * that into an empty list is the exact mistake design D9 names: a spike watched a
     * folder that existed and accepted a write moments later answer an empty listing, and
     * everything downstream — the history saying "no copies yet", retention counting from
     * zero — would then be reasoning from a reading that never happened.
     */
    override suspend fun list(): Either<BackupError, List<StoredBackup>> =
        withContext(Dispatchers.IO) {
            reachable().flatMap { directory ->
                Either.catch {
                    val entries = directory.listFiles()
                        ?: throw IOException("The folder could not be read")

                    entries.filter { it.isFile && isBackupFileName(it.name) }
                        .map { it.asStoredBackup() }
                        .sortedWith(NEWEST_FIRST)
                }.mapLeft { it.toBackupError(BackupError.EXPORT_FAILED) }
            }
        }

    override suspend fun copyOut(
        backup: StoredBackup,
        destinationPath: String,
    ): Either<BackupError, Boolean> = withContext(Dispatchers.IO) {
        reachable().flatMap { directory ->
            val file = File(directory, backup.name)
            if (!file.exists()) return@flatMap false.right()

            Either.catch {
                file.copyTo(File(destinationPath), overwrite = true)
                true
            }.mapLeft { it.toBackupError(BackupError.EXPORT_FAILED) }
        }
    }

    /**
     * Removes one copy, once the file has been confirmed by its content to be one this app
     * wrote — the promise that lets the vault sweep a folder full of somebody's own files.
     *
     * Proving it costs a copy: the gate opens a database with Room, Room writes to what it
     * opens, and the file in the folder is one the app may turn out not to be allowed to
     * touch at all. So the copy comes into the app's own temporary area, is read there, and
     * leaves with everything a database opening put beside it — a refusal then leaves the
     * person's folder exactly as it found it, bytes and entries both.
     *
     * A copy that is already gone is answered as removed: the folder is one the user can
     * also reach with a file manager, and there is nothing left to refuse. A folder that
     * cannot be reached at all is a different answer, and refuses, because nothing is known
     * about the file either way.
     *
     * The journal files are swept beside the copy even though nothing puts them there any
     * more: a folder that was swept by an earlier build may still be holding a pair, and
     * this is the only thing that ever looks at that name again.
     *
     * **False means one thing only: the content check refused.** A deletion that did not
     * happen is a failure and leaves as one — the screen turns false into a sentence about
     * what the file *is*, and saying that over a file the app never managed to unlink would
     * be a claim it has no evidence for.
     */
    override suspend fun remove(backup: StoredBackup): Either<BackupError, Boolean> {
        val directory = withContext(Dispatchers.IO) { reachable() }
            .getOrNull() ?: return BackupError.EXPORT_FAILED.left()

        val file = File(directory, backup.name)
        val exists = withContext(Dispatchers.IO) { file.exists() }
        if (!exists) return true.right()

        val scratch = files.newCapturePath().getOrElse { return it.left() }
        try {
            val read = copyOut(backup, scratch).getOrElse { return it.left() }
            if (!read) return true.right()
            if (!ownCopy.confirms(scratch)) return false.right()
        } finally {
            withContext(NonCancellable) { files.discard(scratch) }
        }

        return withContext(Dispatchers.IO) {
            Either.catch {
                DATABASE_FILES.forEach { suffix -> File(file.absolutePath + suffix).delete() }
                if (file.exists()) throw IOException("The copy could not be removed")
                true
            }.mapLeft { it.toBackupError(BackupError.EXPORT_FAILED) }
        }
    }

    /**
     * The chosen folder as it stands — never as it could be made to stand.
     *
     * Both refusals are the same to a caller and different in kind: nothing was ever
     * pointed at, or what was pointed at is not there now. What separates them for a person
     * is [com.neoutils.finsight.ui.screen.backup.service.FolderLink], which the screen
     * reads, and this stays the destination's own flat "I cannot".
     */
    private fun reachable(): Either<BackupError, File> {
        val chosen = folder.chosenFolder() ?: return BackupError.EXPORT_FAILED.left()
        return if (chosen.isDirectory) chosen.right() else BackupError.EXPORT_FAILED.left()
    }
}

