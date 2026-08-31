@file:OptIn(ExperimentalForeignApi::class, ExperimentalTime::class)

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
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
// A category on `NSDate`, which the interop states as an extension of this package rather
// than as a member — so it is imported by name, like any other extension.
import platform.Foundation.timeIntervalSince1970

/**
 * The app's own sandbox, which on iOS is the whole of what the platform lets an app keep
 * without being pointed at something.
 *
 * It protects against everything except losing the sandbox, and losing the sandbox is what
 * uninstalling does: the system removes the container whole, with the keychain as the only
 * exception (design D3). The screen is what says that out loud; this only has to be
 * somewhere the app can always write.
 *
 * Application Support rather than Documents, because these are files the app maintains and
 * not documents the user authors, and excluded from iCloud's backup for the same reason the
 * live database is (`Database.ios.kt`): the copies hold everything the archive holds, and
 * design D14 keeps this app from putting the user's finances anywhere they did not choose.
 */
class IosBackupDestination(private val ownCopy: OwnCopyCheck) : BackupDestination {

    override suspend fun put(
        capturedPath: String,
        name: String,
    ): Either<BackupError, StoredBackup> = withContext(Dispatchers.Default) {
        val directory = backupDirectory() ?: return@withContext BackupError.EXPORT_FAILED.left()
        val free = freeBackupFileName(name) {
            NSFileManager.defaultManager.fileExistsAtPath("$directory/$it")
        }

        copyItem(capturedPath, "$directory/$free", BackupError.EXPORT_FAILED).flatMap { path ->
            storedBackupAt(path, free)?.right() ?: BackupError.EXPORT_FAILED.left()
        }
    }

    override suspend fun list(): Either<BackupError, List<StoredBackup>> =
        withContext(Dispatchers.Default) {
            val directory = backupDirectory()
                ?: return@withContext BackupError.EXPORT_FAILED.left()

            NSFileManager.defaultManager.contentsOfDirectoryAtPath(directory, error = null)
                .orEmpty()
                .filterIsInstance<String>()
                .filter(::isBackupFileName)
                .mapNotNull { storedBackupAt("$directory/$it", it) }
                .sortedWith(NEWEST_FIRST)
                .right()
        }

    override suspend fun copyOut(
        backup: StoredBackup,
        destinationPath: String,
    ): Either<BackupError, Boolean> = withContext(Dispatchers.Default) {
        val directory = backupDirectory()
            ?: return@withContext BackupError.EXPORT_FAILED.left()

        val path = "$directory/${backup.name}"
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) {
            return@withContext false.right()
        }

        // `copyItemAtPath` refuses a destination that already holds a file, and the caller
        // asked for a free path of its own — so anything left there is this app's own
        // leftover and is in the way of the copy that was asked for.
        NSFileManager.defaultManager.removeItemAtPath(destinationPath, error = null)

        copyItem(path, destinationPath, BackupError.EXPORT_FAILED).map { true }
    }

    /**
     * The journal files go with the copy, because a copy is up to three files while
     * something has it open in write-ahead logging — and the confirmation above opens it
     * with Room. Removing the main file alone would leave two behind that nothing lists
     * and nothing else will ever remove.
     */
    override suspend fun remove(backup: StoredBackup): Either<BackupError, Boolean> {
        val directory = withContext(Dispatchers.Default) { backupDirectory() }
            ?: return BackupError.EXPORT_FAILED.left()

        val path = "$directory/${backup.name}"
        val exists = withContext(Dispatchers.Default) {
            NSFileManager.defaultManager.fileExistsAtPath(path)
        }
        if (!exists) return true.right()
        if (!ownCopy.confirms(path)) return false.right()

        return withContext(Dispatchers.Default) {
            DATABASE_FILES.forEach { suffix ->
                NSFileManager.defaultManager.removeItemAtPath(path + suffix, error = null)
            }
            (!NSFileManager.defaultManager.fileExistsAtPath(path)).right()
        }
    }
}

/**
 * The folder the copies live in, made if it is not there yet, or null when the sandbox
 * refuses — which is the one condition under which this destination has nowhere to write.
 *
 * `internal` because the copy taken before a migration goes here too and is written before
 * this class exists — one folder, decided once, rather than a second walk to Application
 * Support that could drift from this one.
 */
internal fun backupDirectory(): String? {
    val support = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )?.path ?: return null

    val path = "$support/$BACKUP_DIRECTORY"
    val created = NSFileManager.defaultManager.createDirectoryAtPath(
        path = path,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    if (!created) return null

    NSURL.fileURLWithPath(path).setResourceValue(
        value = true,
        forKey = NSURLIsExcludedFromBackupKey,
        error = null,
    )
    return path
}

/**
 * What the file system says about one copy, or null when it says nothing — a file removed
 * between being listed and being described is not an error, it is a file that is gone.
 */
private fun storedBackupAt(path: String, name: String): StoredBackup? {
    val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null)
        ?: return null
    val size = attributes[NSFileSize].asLong() ?: return null
    val modified = (attributes[NSFileModificationDate] as? NSDate)?.timeIntervalSince1970
        ?: return null

    return StoredBackup(
        name = name,
        savedAt = Instant.fromEpochMilliseconds((modified * MILLIS_PER_SECOND).toLong()),
        sizeInBytes = size,
    )
}

/**
 * A count out of a Foundation dictionary, whichever side of the bridge it arrives on:
 * Foundation states a file's size as an `NSNumber`, and the interop is free to have turned
 * it into a Kotlin number already by the time it is read out of a `Map<Any?, *>`. It is
 * shared with the folder rung, which asks a url for the same number under another key.
 */
internal fun Any?.asLong(): Long? = when (this) {
    is NSNumber -> longLongValue
    is Number -> toLong()
    else -> null
}

private const val BACKUP_DIRECTORY = "backups"
internal const val MILLIS_PER_SECOND = 1_000
