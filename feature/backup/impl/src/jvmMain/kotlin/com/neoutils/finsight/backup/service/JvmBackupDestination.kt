@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.backup.service

import arrow.core.Either
import arrow.core.right
import com.neoutils.finsight.database.defaultDatabasePath
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
import java.nio.file.Files
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The desktop's own storage, which is the one platform where it is also the folder the
 * user could point at.
 *
 * `~/.finance/` survives everything a desktop does to an app — there is no uninstall that
 * empties a home directory — so the two steps of protection the mobile platforms need
 * coincide here (design D3), and this is the whole of what the desktop ever requires.
 *
 * The folder is taken from the database's own path rather than spelled out again: the
 * directory the archive lives in is `:core:database`'s to decide, and a second copy of
 * `.finance` here would be a second decision that could drift from it.
 */
class JvmBackupDestination(
    private val ownCopy: OwnCopyCheck,
    private val directory: File = defaultBackupDirectory(),
) : BackupDestination {

    override suspend fun put(
        capturedPath: String,
        name: String,
    ): Either<BackupError, StoredBackup> = withContext(Dispatchers.IO) {
        Either.catch {
            directory.mkdirs()
            val free = freeBackupFileName(name) { File(directory, it).exists() }
            copyIntoPlace(File(capturedPath), File(directory, free)).asStoredBackup()
        }.mapLeft { it.toBackupError(BackupError.EXPORT_FAILED) }
    }

    /**
     * What the folder holds — and a refusal wherever the reading did not happen.
     *
     * A directory that is not there yet is empty and says so: this one is the app's own, it
     * is made by the first [put], and nothing has been captured before that. A directory
     * that *is* there and answers null to `listFiles` is the other case entirely, and it is
     * a refusal: null covers a read that failed as well as a path that is not a directory,
     * and turning it into an empty list would have the screen say "no copies yet" over a
     * folder it never managed to read (design D9).
     */
    override suspend fun list(): Either<BackupError, List<StoredBackup>> =
        withContext(Dispatchers.IO) {
            Either.catch {
                val files = when {
                    !directory.exists() -> emptyArray()
                    else -> directory.listFiles()
                        ?: throw IOException("The folder could not be read")
                }

                files.filter { it.isFile && isBackupFileName(it.name) }
                    .map { it.asStoredBackup() }
                    .sortedWith(NEWEST_FIRST)
            }.mapLeft { it.toBackupError(BackupError.EXPORT_FAILED) }
        }

    override suspend fun copyOut(
        backup: StoredBackup,
        destinationPath: String,
    ): Either<BackupError, Boolean> = withContext(Dispatchers.IO) {
        val file = File(directory, backup.name)
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
        val file = File(directory, backup.name)
        val exists = withContext(Dispatchers.IO) { file.exists() }
        if (!exists) return true.right()
        if (!ownCopy.confirms(file.absolutePath)) return false.right()

        return withContext(Dispatchers.IO) {
            Either.catch {
                DATABASE_FILES.forEach { suffix -> File(file.absolutePath + suffix).delete() }
                if (file.exists()) throw IOException("The copy could not be removed")
                true
            }.mapLeft { it.toBackupError(BackupError.EXPORT_FAILED) }
        }
    }
}

/**
 * Copies [source] into [target] through a name nothing lists, and puts it in place only once
 * every byte is there.
 *
 * A copy written straight to its final name carries a name the app recognises from its first
 * byte. A write cut short — a full disk, a volume detached, a process killed — then leaves a
 * truncated file that [BackupDestination.list] shows, that retention counts inside the
 * window it keeps, and that [BackupDestination.remove] refuses for good: a truncated
 * database reads as corrupt, and corruption is not proof a file is this app's
 * ([OwnCopyCheck]). One such file costs one real copy at every capture from then on.
 *
 * Putting it in place is a rename inside one directory, which is the file system's own
 * single step, and it refuses a target that is already there rather than replacing it — the
 * name was free a moment ago, and a copy that arrived meanwhile is not this one's to
 * overwrite. Whatever did not get there is removed on the way out.
 *
 * `internal` because both rungs of the desktop write the same way, and a second spelling
 * would be a second answer to when a copy becomes a copy.
 */
internal fun copyIntoPlace(source: File, target: File): File {
    val staged = File(target.absolutePath + STAGED_SUFFIX)
    return try {
        source.copyTo(staged, overwrite = true)
        Files.move(staged.toPath(), target.toPath())
        target
    } finally {
        staged.delete()
    }
}

/**
 * What the file system says about one copy. `internal` because both rungs of the desktop
 * read it the same way, and a second spelling would be a second answer to when a copy was
 * written.
 */
internal fun File.asStoredBackup() = StoredBackup(
    name = name,
    savedAt = Instant.fromEpochMilliseconds(lastModified()),
    sizeInBytes = length(),
)

/**
 * Beside the archive, in the directory `:core:database` puts it in.
 *
 * `internal` because the copy taken before a migration goes here too and is written before
 * this class exists — one folder, decided once, rather than a second spelling of `.finance`
 * that could drift from this one.
 */
internal fun defaultBackupDirectory(): File =
    File(File(defaultDatabasePath()).absoluteFile.parentFile, BACKUP_DIRECTORY)

private const val BACKUP_DIRECTORY = "backups"
