@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.backup.service

import android.content.Context
import arrow.core.Either
import arrow.core.right
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.ui.screen.backup.service.BackupDestination
import com.neoutils.finsight.ui.screen.backup.service.NEWEST_FIRST
import com.neoutils.finsight.ui.screen.backup.service.OwnCopyCheck
import com.neoutils.finsight.ui.screen.backup.service.STAGED_SUFFIX
import com.neoutils.finsight.ui.screen.backup.service.StoredBackup
import com.neoutils.finsight.ui.screen.backup.service.freeBackupFileName
import com.neoutils.finsight.ui.screen.backup.service.isBackupFileName
import java.io.File
import java.io.IOException
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The app's own storage, which on Android is a folder no permission reaches and the
 * uninstaller empties.
 *
 * That is the honest description of what this protects against and what it does not
 * (design D3): a phone that is wiped, an app that is uninstalled, and a copy of the app
 * installed somewhere else all leave nothing behind here — the platform documents
 * `getExternalFilesDir` as removed with the package. It is the destination the vault
 * starts at because it needs nothing from the user and can never be revoked, and the
 * screen is what says out loud what it costs.
 *
 * External rather than internal, so that a person who wants their copies off the phone can
 * reach them with a file manager without this app arranging anything. It falls back to the
 * internal directory when there is no external volume mounted, which is the only condition
 * under which the platform answers nothing.
 */
class AndroidBackupDestination(
    private val appContext: Context,
    private val ownCopy: OwnCopyCheck,
) : BackupDestination {

    override suspend fun put(
        capturedPath: String,
        name: String,
    ): Either<BackupError, StoredBackup> = withContext(Dispatchers.IO) {
        Either.catch {
            val directory = directory()
            val free = freeBackupFileName(name) { File(directory, it).exists() }
            copyIntoPlace(File(capturedPath), File(directory, free)).asStoredBackup()
        }.mapLeft { it.toBackupError(BackupError.EXPORT_FAILED) }
    }

    /**
     * What the folder holds — and a refusal wherever the reading did not happen.
     *
     * `listFiles` answers null both for a directory it could not read and for a path that is
     * not a directory, and here the two say the same thing: the reading did not happen.
     * There is no third case for it to mean — [backupDirectory] makes the folder on the way
     * in, so "not there yet" is never what a null is — and turning it into an empty list
     * would have the screen say "no copies yet" over a folder it never managed to read, the
     * card overwritten with zero, and a migration report nothing to carry (design D9).
     */
    override suspend fun list(): Either<BackupError, List<StoredBackup>> =
        withContext(Dispatchers.IO) {
            Either.catch {
                val entries = directory().listFiles()
                    ?: throw IOException("The folder could not be read")

                entries.filter { it.isFile && isBackupFileName(it.name) }
                    .map { it.asStoredBackup() }
                    .sortedWith(NEWEST_FIRST)
            }.mapLeft { it.toBackupError(BackupError.EXPORT_FAILED) }
        }

    override suspend fun copyOut(
        backup: StoredBackup,
        destinationPath: String,
    ): Either<BackupError, Boolean> = withContext(Dispatchers.IO) {
        val file = File(directory(), backup.name)
        if (!file.exists()) return@withContext false.right()

        Either.catch {
            file.copyTo(File(destinationPath), overwrite = true)
            true
        }.mapLeft { it.toBackupError(BackupError.EXPORT_FAILED) }
    }

    /**
     * The journal files go with the copy, because a copy is up to three files while
     * something has it open in write-ahead logging — and the confirmation above opens it
     * with Room. Removing the main file alone would leave two behind that nothing lists
     * and nothing else will ever remove.
     *
     * **False means one thing only: the content check refused.** A deletion that did not
     * happen is a failure and leaves as one — the screen turns false into a sentence about
     * what the file *is*, and saying that over a file the app never managed to unlink would
     * be a claim it has no evidence for.
     */
    override suspend fun remove(backup: StoredBackup): Either<BackupError, Boolean> {
        val file = withContext(Dispatchers.IO) {
            File(directory(), backup.name).takeIf { it.exists() }
        } ?: return true.right()

        if (!ownCopy.confirms(file.absolutePath)) return false.right()

        return withContext(Dispatchers.IO) {
            Either.catch {
                DATABASE_FILES.forEach { suffix -> File(file.absolutePath + suffix).delete() }
                if (file.exists()) throw IOException("The copy could not be removed")
                true
            }.mapLeft { it.toBackupError(BackupError.EXPORT_FAILED) }
        }
    }

    /** Touches the file system, so every caller of it is already off the main thread. */
    private fun directory(): File = backupDirectory(appContext)
}

/**
 * The folder the copies live in, made if it is not there yet.
 *
 * Top-level and `internal` because the copy taken before a migration goes here too and is
 * written before this class exists — one folder, decided once, rather than a second reading
 * of `getExternalFilesDir` that could drift from this one.
 */
internal fun backupDirectory(context: Context): File =
    File(context.getExternalFilesDir(null) ?: context.filesDir, BACKUP_DIRECTORY)
        .apply { mkdirs() }

/**
 * Copies [source] into [target] through a name nothing lists, and puts it in place only once
 * every byte is there.
 *
 * A copy written straight to its final name carries a name the app recognises from its first
 * byte. A write cut short — a full disk, a process killed — then leaves a truncated file that
 * [BackupDestination.list] shows, that retention counts inside the window it keeps, and that
 * [BackupDestination.remove] refuses for good: a truncated database reads as corrupt, and
 * corruption is not proof a file is this app's ([OwnCopyCheck]). One such file costs one real
 * copy at every capture from then on.
 *
 * Putting it in place is a rename inside one directory, which is the file system's own single
 * step. Whatever did not get there is removed on the way out.
 */
private fun copyIntoPlace(source: File, target: File): File {
    val staged = File(target.absolutePath + STAGED_SUFFIX)
    return try {
        source.copyTo(staged, overwrite = true)
        // A rename replaces whatever is at the destination, and a copy that arrived under
        // this name while this one was being written is not this one's to overwrite. The
        // desktop gets the same refusal from `Files.move`, which this platform's floor of
        // API 24 does not reach.
        if (target.exists()) throw IOException("The name was taken while the copy was written")
        if (!staged.renameTo(target)) throw IOException("The copy could not be put in place")
        target
    } finally {
        staged.delete()
    }
}

private fun File.asStoredBackup() = StoredBackup(
    name = name,
    savedAt = Instant.fromEpochMilliseconds(lastModified()),
    sizeInBytes = length(),
)

private const val BACKUP_DIRECTORY = "backups"
