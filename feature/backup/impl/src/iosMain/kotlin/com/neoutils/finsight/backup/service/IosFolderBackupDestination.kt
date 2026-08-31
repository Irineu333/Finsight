@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class, ExperimentalTime::class)

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
import com.neoutils.finsight.ui.screen.backup.service.STAGED_SUFFIX
import com.neoutils.finsight.ui.screen.backup.service.StoredBackup
import com.neoutils.finsight.ui.screen.backup.service.freeBackupFileName
import com.neoutils.finsight.ui.screen.backup.service.isBackupFileName
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import platform.Foundation.NSDate
import platform.Foundation.NSDirectoryEnumerationSkipsHiddenFiles
import platform.Foundation.NSError
import platform.Foundation.NSFileCoordinatorWritingForDeleting
import platform.Foundation.NSFileCoordinatorWritingForReplacing
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSURLContentModificationDateKey
import platform.Foundation.NSURLFileSizeKey
// A category on `NSDate`, which the interop states as an extension of this package rather
// than as a member — so it is imported by name, like any other extension.
import platform.Foundation.timeIntervalSince1970

/**
 * The second rung on iOS: the same four operations as [IosBackupDestination], over the
 * folder somebody pointed at instead of the sandbox the uninstaller removes whole.
 *
 * **It is a class of its own and not the first rung over another url**, because the two
 * disagree about what has to be established before *absence* may be said. The app's own
 * folder is there because the app makes it and nothing else can take it away, so an empty
 * listing of it is the truth on its own. A folder somebody chose sits behind a bookmark that
 * may not resolve, a security scope that may have expired, and a provider that may have been
 * signed out of — and every one of those is design D9's forbidden sentence waiting to be
 * said: **zero copies must never be read as "there is nothing here" until the app has
 * established that the folder is there.**
 *
 * **One listing is never proof of absence; two are.** Nothing here asks the file system a
 * single question. [IosBackupFolder.withOwnFolder] cannot hand over a folder without having
 * enumerated it a moment earlier, over this scope and through this provider, so a listing
 * that then comes back empty is a folder with nothing in it rather than a folder nobody
 * could read.
 *
 * **Nothing here creates a directory.** Not on a write, not after a listing came back empty,
 * not ever: only [IosBackupFolder.point] makes the app's own subfolder, with a person in
 * front of the screen.
 *
 * **Every write and every removal is coordinated** (task 11.4). The folder is one the person
 * also reaches from Files and whatever sync client they use, so a change to it is announced
 * through `NSFileCoordinator` rather than made behind everyone's back — and a read is
 * coordinated too, which is what makes a provider materialise a copy it is holding in the
 * cloud instead of handing over a file of no bytes.
 *
 * **The copies in the folder are read and never moved, and only this app's are removed.**
 * The folder belongs to the person and may hold their files; [remove] proves a file is this
 * app's by reading it ([OwnCopyCheck]) before it takes it away. Proving it costs a copy — the
 * gate opens a database with Room and Room opens a path — so the file comes into the app's
 * own temporary area first, and everything a database opening leaves beside it is left
 * there and never in the person's folder.
 */
class IosFolderBackupDestination(
    private val folder: IosBackupFolder,
    private val ownCopy: OwnCopyCheck,
    /**
     * Where a copy is read for the gate that proves it is this app's. It is the service that
     * already owns the decision about the app's own temporary area and already knows every
     * file a database opening leaves behind.
     */
    private val files: BackupFileService,
) : BackupDestination {

    /**
     * The name is asked for, and a name already taken is stepped around rather than written
     * over: the copy standing under it is the one thing nobody wants back.
     *
     * The write is coordinated as a replacement even though the name was just found free,
     * which is Apple's own instruction — the option exists to close the race between finding
     * a name free and writing to it, so it is used whether or not anything is in the way. It
     * does not make the copy overwrite: `copyItemAtURL` still refuses a destination that
     * holds a file, so a copy that lost that race fails and is taken again at the next
     * trigger.
     */
    override suspend fun put(
        capturedPath: String,
        name: String,
    ): Either<BackupError, StoredBackup> = withContext(Dispatchers.Default) {
        folder.withOwnFolder(BackupError.EXPORT_FAILED) { own ->
            val free = freeBackupFileName(name) { own.holds(it) }
            val target = own.URLByAppendingPathComponent(free)
                ?: return@withOwnFolder BackupError.EXPORT_FAILED.left()
            // Where the copy is written until every byte of it is there. It is built off the
            // folder's own url like the target is, never off the target's text: a scoped url
            // that goes through a string comes back opening nothing (design D2).
            val staged = own.URLByAppendingPathComponent(free + STAGED_SUFFIX)
                ?: return@withOwnFolder BackupError.EXPORT_FAILED.left()

            coordinateWriting(
                url = target,
                options = NSFileCoordinatorWritingForReplacing,
                otherwise = BackupError.EXPORT_FAILED,
            ) { url ->
                // A write cut short under the final name leaves a truncated file in the
                // person's own folder that the history shows, retention counts inside the
                // window it keeps, and removal refuses for good — a truncated database reads
                // as corrupt, and corruption is not proof a file is this app's. One such
                // file costs one real copy at every capture from then on.
                NSFileManager.defaultManager.removeItemAtURL(staged, error = null)

                val landed = copyUrl(NSURL.fileURLWithPath(capturedPath), staged).flatMap {
                    if (NSFileManager.defaultManager.moveItemAtURL(staged, url, error = null)) {
                        url.describedAs(free)?.right() ?: BackupError.EXPORT_FAILED.left()
                    } else {
                        BackupError.EXPORT_FAILED.left()
                    }
                }

                NSFileManager.defaultManager.removeItemAtURL(staged, error = null)
                landed
            }
        }
    }

    /**
     * What the folder holds, which may be nothing — see the class comment for why nothing is
     * an answer here and not a refusal.
     *
     * A copy the file system will not describe is left out rather than made up: a file
     * removed between being listed and being asked about is a file that is gone. That is
     * also what keeps a directory out of the answer, since a directory has no file size to
     * report.
     */
    override suspend fun list(): Either<BackupError, List<StoredBackup>> =
        withContext(Dispatchers.Default) {
            folder.withOwnFolder(BackupError.EXPORT_FAILED) { own ->
                coordinateReading(own, BackupError.EXPORT_FAILED) { url ->
                    val children = memScoped {
                        val failure = alloc<ObjCObjectVar<NSError?>>()
                        NSFileManager.defaultManager.contentsOfDirectoryAtURL(
                            url = url,
                            includingPropertiesForKeys = DESCRIBING_KEYS,
                            options = NSDirectoryEnumerationSkipsHiddenFiles,
                            error = failure.ptr,
                        ) ?: return@coordinateReading failure.value
                            .toBackupError(BackupError.EXPORT_FAILED)
                            .left()
                    }

                    children.filterIsInstance<NSURL>()
                        .mapNotNull { child ->
                            child.lastPathComponent
                                ?.takeIf(::isBackupFileName)
                                ?.let(child::describedAs)
                        }
                        .sortedWith(NEWEST_FIRST)
                        .right()
                }
            }
        }

    /**
     * The copy in the folder is read and left exactly as it was.
     *
     * A copy a trustworthy listing does not hold answers false: the folder is one the person
     * can also reach with Files, and there is nothing left to refuse. A folder that could not
     * be read at all is a different answer and refuses.
     */
    override suspend fun copyOut(
        backup: StoredBackup,
        destinationPath: String,
    ): Either<BackupError, Boolean> = withContext(Dispatchers.Default) {
        folder.withOwnFolder(BackupError.EXPORT_FAILED) { own ->
            val source = own.URLByAppendingPathComponent(backup.name)
                ?: return@withOwnFolder BackupError.EXPORT_FAILED.left()
            if (!source.checkResourceIsReachableAndReturnError(null)) {
                return@withOwnFolder false.right()
            }

            coordinateReading(source, BackupError.EXPORT_FAILED) { url ->
                val target = NSURL.fileURLWithPath(destinationPath)
                // `copyItemAtURL` refuses a destination that already holds a file, and the
                // caller asked for a path of its own — so anything left there is this app's
                // own leftover and is in the way of the copy that was asked for.
                NSFileManager.defaultManager.removeItemAtURL(target, error = null)
                copyUrl(url, target).map { true }
            }
        }
    }

    /**
     * Removes one copy, once the file has been confirmed by its content to be one this app
     * wrote — the promise that lets the vault sweep a folder full of somebody's own files.
     *
     * A copy that is already gone is answered as removed. A folder that could not be read at
     * all refuses, because nothing is known about the file either way.
     */
    override suspend fun remove(backup: StoredBackup): Either<BackupError, Boolean> {
        val scratch = files.newCapturePath().getOrElse { return it.left() }
        try {
            val read = copyOut(backup, scratch).getOrElse { return it.left() }
            if (!read) return true.right()
            if (!ownCopy.confirms(scratch)) return false.right()
        } finally {
            withContext(NonCancellable) { files.discard(scratch) }
        }

        return withContext(Dispatchers.Default) {
            folder.withOwnFolder(BackupError.EXPORT_FAILED) { own ->
                val target = own.URLByAppendingPathComponent(backup.name)
                    ?: return@withOwnFolder BackupError.EXPORT_FAILED.left()

                coordinateWriting(
                    url = target,
                    options = NSFileCoordinatorWritingForDeleting,
                    otherwise = BackupError.EXPORT_FAILED,
                ) { url -> removeUrl(url) }
            }
        }
    }
}

/**
 * Whether the folder already holds something under [name].
 *
 * A url that cannot be built from the name answers *free*, deliberately: the alternative
 * answer would send [freeBackupFileName] looking for the next one forever, and the copy that
 * follows fails cleanly on the same url a moment later.
 */
private fun NSURL.holds(name: String): Boolean =
    URLByAppendingPathComponent(name)?.checkResourceIsReachableAndReturnError(null) == true

/**
 * What the file system says about one copy, or null when it says nothing — a file removed
 * between being listed and being described is not an error, it is a file that is gone, and
 * a directory has no size to report at all.
 */
private fun NSURL.describedAs(name: String): StoredBackup? = memScoped {
    val values = resourceValuesForKeys(DESCRIBING_KEYS, error = null) ?: return@memScoped null
    val size = values[NSURLFileSizeKey].asLong() ?: return@memScoped null
    val modified = (values[NSURLContentModificationDateKey] as? NSDate)?.timeIntervalSince1970
        ?: return@memScoped null

    StoredBackup(
        name = name,
        savedAt = Instant.fromEpochMilliseconds((modified * MILLIS_PER_SECOND).toLong()),
        sizeInBytes = size,
    )
}

private fun copyUrl(source: NSURL, target: NSURL): Either<BackupError, Unit> = memScoped {
    val failure = alloc<ObjCObjectVar<NSError?>>()
    val copied = NSFileManager.defaultManager.copyItemAtURL(source, target, failure.ptr)
    if (copied) {
        Unit.right()
    } else {
        failure.value.toBackupError(BackupError.EXPORT_FAILED).left()
    }
}

private fun removeUrl(target: NSURL): Either<BackupError, Boolean> = memScoped {
    val failure = alloc<ObjCObjectVar<NSError?>>()
    val removed = NSFileManager.defaultManager.removeItemAtURL(target, failure.ptr)
    if (removed) {
        true.right()
    } else {
        failure.value.toBackupError(BackupError.EXPORT_FAILED).left()
    }
}

/** Everything a copy is described by, asked for once and read back off each url. */
private val DESCRIBING_KEYS = listOf(NSURLFileSizeKey, NSURLContentModificationDateKey)
