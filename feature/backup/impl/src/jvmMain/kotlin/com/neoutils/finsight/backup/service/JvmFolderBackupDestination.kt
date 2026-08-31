package com.neoutils.finsight.backup.service

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.ui.screen.backup.service.BackupDestination
import com.neoutils.finsight.ui.screen.backup.service.NEWEST_FIRST
import com.neoutils.finsight.ui.screen.backup.service.OwnCopyCheck
import com.neoutils.finsight.ui.screen.backup.service.StoredBackup
import com.neoutils.finsight.ui.screen.backup.service.freeBackupFileName
import com.neoutils.finsight.ui.screen.backup.service.isBackupFileName
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The second rung on the desktop: the same four operations as [JvmBackupDestination], over
 * the folder somebody pointed at instead of the app's own.
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
 * **Nothing here creates a directory.** Not on a write, not after a listing came back
 * empty, not ever: only [JvmBackupFolder.point] makes the app's own subfolder, with a
 * person in front of the screen. A destination that rebuilt it would, on the day a network
 * volume is not mounted, write copies into a mountpoint on the local disk while the archive
 * it is supposed to be adding to sits on the disk that is missing — the desktop's own shape
 * of the split archive design D9 refuses.
 *
 * **The copies in the folder are read and never moved.** Retention confirms by content
 * before removing anything ([OwnCopyCheck]), so a folder holding the user's own files loses
 * none of them, which is the whole reason the app keeps to a subfolder of its own.
 */
class JvmFolderBackupDestination(
    private val folder: JvmBackupFolder,
    private val ownCopy: OwnCopyCheck,
) : BackupDestination {

    override suspend fun put(
        capturedPath: String,
        name: String,
    ): Either<BackupError, StoredBackup> = withContext(Dispatchers.IO) {
        reachable().flatMap { directory ->
            Either.catch {
                val free = freeBackupFileName(name) { File(directory, it).exists() }
                File(capturedPath).copyTo(File(directory, free)).asStoredBackup()
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
                    val files = directory.listFiles()
                        ?: throw IOException("The folder could not be read")

                    files.filter { it.isFile && isBackupFileName(it.name) }
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
     * A copy that is already gone is answered as removed: the folder is one the user can
     * also reach with a file manager, and there is nothing left to refuse. A folder that
     * cannot be reached at all is a different answer, and refuses, because nothing is known
     * about the file either way.
     */
    override suspend fun remove(backup: StoredBackup): Either<BackupError, Boolean> {
        val directory = withContext(Dispatchers.IO) { reachable() }
            .getOrNull() ?: return BackupError.EXPORT_FAILED.left()

        val file = File(directory, backup.name)
        val exists = withContext(Dispatchers.IO) { file.exists() }
        if (!exists) return true.right()
        if (!ownCopy.confirms(file.absolutePath)) return false.right()

        return withContext(Dispatchers.IO) {
            Either.catch {
                DATABASE_FILES.forEach { suffix -> File(file.absolutePath + suffix).delete() }
                !file.exists()
            }.mapLeft { it.toBackupError(BackupError.EXPORT_FAILED) }
        }
    }

    /**
     * The app's own subfolder inside the chosen one, as it stands — never as it could be
     * made to stand.
     *
     * Both refusals are the same to a caller and different in kind: nothing was ever
     * pointed at, or what was pointed at is not there now. What separates them for a person
     * is [com.neoutils.finsight.ui.screen.backup.service.FolderLink], which the screen
     * reads, and this stays the destination's own flat "I cannot".
     */
    private fun reachable(): Either<BackupError, File> {
        val own = folder.ownFolder() ?: return BackupError.EXPORT_FAILED.left()
        return if (own.isDirectory) own.right() else BackupError.EXPORT_FAILED.left()
    }
}

