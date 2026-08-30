@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.backup.service

import android.content.Context
import arrow.core.Either
import arrow.core.right
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.ui.screen.backup.service.BackupDestination
import com.neoutils.finsight.ui.screen.backup.service.NEWEST_FIRST
import com.neoutils.finsight.ui.screen.backup.service.OwnCopyCheck
import com.neoutils.finsight.ui.screen.backup.service.StoredBackup
import com.neoutils.finsight.ui.screen.backup.service.freeBackupFileName
import com.neoutils.finsight.ui.screen.backup.service.isBackupFileName
import java.io.File
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
            File(capturedPath).copyTo(File(directory, free)).asStoredBackup()
        }.mapLeft { it.toBackupError(BackupError.EXPORT_FAILED) }
    }

    override suspend fun list(): Either<BackupError, List<StoredBackup>> =
        withContext(Dispatchers.IO) {
            Either.catch {
                directory().listFiles().orEmpty()
                    .filter { it.isFile && isBackupFileName(it.name) }
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
     */
    override suspend fun remove(backup: StoredBackup): Either<BackupError, Boolean> {
        val file = withContext(Dispatchers.IO) {
            File(directory(), backup.name).takeIf { it.exists() }
        } ?: return true.right()

        if (!ownCopy.confirms(file.absolutePath)) return false.right()

        return withContext(Dispatchers.IO) {
            Either.catch {
                DATABASE_FILES.forEach { suffix -> File(file.absolutePath + suffix).delete() }
                !file.exists()
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

private fun File.asStoredBackup() = StoredBackup(
    name = name,
    savedAt = Instant.fromEpochMilliseconds(lastModified()),
    sizeInBytes = length(),
)

private const val BACKUP_DIRECTORY = "backups"
