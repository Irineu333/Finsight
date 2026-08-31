package com.neoutils.finsight.backup.service

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
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
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileWriteOutOfSpaceError
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeData

/**
 * The two file dialogs, over the document picker ([awaitPickedUrls], which owns the
 * delegate UIKit holds weakly).
 *
 * Both directions go through a copy in the app's own temporary area. On the way in the
 * picker already makes one — `asCopy` puts the user's file in the sandbox rather than
 * lending a handle on the original — and it is copied again, because what the verification
 * receives has to be a file it may migrate in place and then throw away. On the way out the
 * picker exports a file by its own name, so the name a backup is offered under is the name
 * of the staged copy it is handed.
 */
@OptIn(ExperimentalForeignApi::class)
class IosBackupFileService : BackupFileService {

    /**
     * The copy is removed unless the path is handed back, and it is a `finally` rather than
     * a failure path because the way it is most easily lost is not a failure: the copy runs
     * to the end whatever the caller's scope is doing, and [withContext] then raises the
     * cancellation instead of returning — the file exists and nobody has been told where.
     * A caller cannot close what it was never given, and the path is minted here. The name
     * is settled before the copy for the same reason: [discard] takes the private directory
     * with it, so a copy that never happened is cleaned up by the path it would have had.
     */
    override suspend fun copyInChosenFile(
        context: PlatformContext,
    ): Either<BackupError, String?> {
        val chosen = context.awaitPickedUrls(BackupError.VERIFICATION_FAILED) {
            UIDocumentPickerViewController(
                // Data is every file that is a file, which is the point: what a candidate
                // is gets settled by reading it, and a narrower type here would hide
                // backups this app reads perfectly well.
                forOpeningContentTypes = listOf(UTTypeData),
                asCopy = true,
            )
        }.getOrElse { return it.left() }.firstOrNull() ?: return null.right()

        var unclaimed: String? = null
        try {
            return withContext(Dispatchers.Default) {
                val directory = createPrivateDirectory()
                    ?: return@withContext BackupError.VERIFICATION_FAILED.left()
                val destination = "$directory/$CANDIDATE_NAME"
                unclaimed = destination
                chosen.copyIntoPrivateFile(destination)
            }.onRight { unclaimed = null }
        } finally {
            unclaimed?.let { withContext(NonCancellable) { discard(it) } }
        }
    }

    override suspend fun copyOutCapturedFile(
        sourcePath: String,
        suggestedName: String,
        context: PlatformContext,
    ): Either<BackupError, Boolean> {
        val directory = withContext(Dispatchers.Default) { createPrivateDirectory() }
            ?: return BackupError.EXPORT_FAILED.left()

        try {
            val staged = withContext(Dispatchers.Default) {
                copyItem(sourcePath, "$directory/$suggestedName", BackupError.EXPORT_FAILED)
            }.getOrElse { return it.left() }

            return context.awaitPickedUrls(BackupError.EXPORT_FAILED) {
                UIDocumentPickerViewController(
                    forExportingURLs = listOf(NSURL.fileURLWithPath(staged)),
                    // The staged copy is this app's to keep and drop; exporting without it
                    // would move the file out from under the temporary directory instead.
                    asCopy = true,
                )
            }.map { it.isNotEmpty() }
        } finally {
            // Hung off the picker having answered rather than off presenting it: the
            // export is a copy the picker makes while it is up, so it is done reading
            // only once it calls back — and both callbacks come through here, as does a
            // screen that went away mid-export, which is why the removal outlives
            // cancellation. One call takes the staged file and the directory with it.
            withContext(NonCancellable + Dispatchers.Default) {
                NSFileManager.defaultManager.removeItemAtPath(directory, error = null)
            }
        }
    }

    override suspend fun newCapturePath(): Either<BackupError, String> =
        withContext(Dispatchers.Default) {
            val directory = createPrivateDirectory()
                ?: return@withContext BackupError.EXPORT_FAILED.left()
            "$directory/$CAPTURE_NAME".right()
        }

    override suspend fun discard(path: String) {
        withContext(Dispatchers.Default) {
            DATABASE_FILES.forEach { suffix ->
                NSFileManager.defaultManager.removeItemAtPath(path + suffix, error = null)
            }
            privateDirectoryOf(path)?.let { directory ->
                NSFileManager.defaultManager.removeItemAtPath(directory, error = null)
            }
        }
    }

    /**
     * A copy of what the user chose, at [destinationPath].
     *
     * The security scope is claimed even though `asCopy` already put the file inside the
     * sandbox: the picker is free to hand back a scoped url instead, and reading one
     * without claiming it fails. It is released in a `finally` because a scope left open is
     * a resource the system counts and does not reclaim.
     */
    private fun NSURL.copyIntoPrivateFile(destinationPath: String): Either<BackupError, String> {
        val claimed = startAccessingSecurityScopedResource()
        try {
            val source = path ?: return BackupError.VERIFICATION_FAILED.left()
            return copyItem(source, destinationPath, BackupError.VERIFICATION_FAILED)
        } finally {
            if (claimed) {
                stopAccessingSecurityScopedResource()
            }
        }
    }
}

/**
 * Somewhere the app may write and the system may reclaim, one directory per call so that
 * a name already used is never a name in the way — `copyItemAtPath` and `VACUUM INTO` both
 * refuse a destination that exists, and every caller here writes under a name it does not
 * get to choose.
 */
@OptIn(ExperimentalForeignApi::class)
private fun createPrivateDirectory(): String? {
    val path = "${privateRoot()}/${NSUUID().UUIDString}"
    val created = NSFileManager.defaultManager.createDirectoryAtPath(
        path = path,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    return path.takeIf { created }
}

/**
 * The directory [path] lies in, when it is one of this service's own: a single level
 * under [privateRoot], which is the only shape [createPrivateDirectory] makes.
 *
 * A path to discard arrives from the caller, and the directory above an arbitrary file is
 * not this service's to remove — hence the check rather than a bare parent.
 */
private fun privateDirectoryOf(path: String): String? {
    val directory = path.substringBeforeLast('/', missingDelimiterValue = "")
    val name = directory.substringAfterLast('/')
    val own = name.isNotEmpty() &&
        name != "." &&
        name != ".." &&
        directory == "${privateRoot()}/$name"
    return directory.takeIf { own }
}

private fun privateRoot() = "${NSTemporaryDirectory().trimEnd('/')}/$PRIVATE_DIRECTORY"

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun copyItem(
    sourcePath: String,
    destinationPath: String,
    otherwise: BackupError,
): Either<BackupError, String> = memScoped {
    val failure = alloc<ObjCObjectVar<NSError?>>()
    val copied = NSFileManager.defaultManager.copyItemAtPath(
        srcPath = sourcePath,
        toPath = destinationPath,
        error = failure.ptr,
    )
    if (copied) destinationPath.right() else failure.value.toBackupError(otherwise).left()
}

/**
 * A full disk is the one failure the user can act on, so it is the one told apart. Cocoa
 * spends a code of its own on it, which is more than a message to match against.
 */
internal fun NSError?.toBackupError(otherwise: BackupError): BackupError =
    if (this?.code == NSFileWriteOutOfSpaceError) BackupError.NO_SPACE else otherwise

/**
 * A database is up to three files while it is open in write-ahead logging, and a
 * candidate is opened with Room. Removing the main file alone would leave the other two
 * behind in the temporary directory.
 */
internal val DATABASE_FILES = listOf("", "-wal", "-shm")

/**
 * What a database opening leaves *beside* the file it opened. It is what has to go when a
 * copy is replaced rather than removed: the main file is overwritten by the replacement, and
 * these belong to the copy that was there before it.
 */
internal val JOURNAL_FILES = DATABASE_FILES.drop(1)

private const val PRIVATE_DIRECTORY = "finsight-backup"
private const val CANDIDATE_NAME = "candidate.db"
private const val CAPTURE_NAME = "capture.db"
