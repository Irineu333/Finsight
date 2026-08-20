package com.neoutils.finsight.backup.service

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.extension.resolvePresenter
import com.neoutils.finsight.ui.screen.backup.service.BackupFileService
import kotlin.coroutines.resume
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileWriteOutOfSpaceError
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeData
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * The document picker, which answers through a delegate rather than through a return
 * value, and holds that delegate weakly.
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

    override suspend fun copyInChosenFile(
        context: PlatformContext,
    ): Either<BackupError, String?> {
        val chosen = context.awaitPickedUrls(BackupError.NOT_A_BACKUP) {
            UIDocumentPickerViewController(
                // Data is every file that is a file, which is the point: what a candidate
                // is gets settled by reading it, and a narrower type here would hide
                // backups this app reads perfectly well.
                forOpeningContentTypes = listOf(UTTypeData),
                asCopy = true,
            )
        }.getOrElse { return it.left() }.firstOrNull() ?: return null.right()

        return withContext(Dispatchers.Default) { chosen.copyIntoPrivateFile() }
    }

    override suspend fun copyOutCapturedFile(
        sourcePath: String,
        suggestedName: String,
        context: PlatformContext,
    ): Either<BackupError, Boolean> {
        val staged = withContext(Dispatchers.Default) {
            val directory = createPrivateDirectory()
                ?: return@withContext BackupError.EXPORT_FAILED.left()
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
    }

    /**
     * Presents a picker and suspends until it answers, as the urls it was given or as an
     * empty list when the user closed it without choosing.
     *
     * The delegate is put in [retainedDelegates] before the picker can reach it and taken
     * out by whichever callback ends the picker's life. That is the whole of why the set
     * exists: [UIDocumentPickerViewController.delegate] is a weak reference, so a delegate
     * held by nothing else is collectable the moment this function returns — the picker
     * would come up, the user would choose a file, and the callback would arrive at
     * nothing. A cancelled continuation deliberately does not release it: the picker is
     * still on screen, still holding the weak reference, and still going to call back.
     * Answering a continuation that is no longer listening costs nothing; being called
     * through a collected delegate does not fail so quietly.
     */
    private suspend fun PlatformContext.awaitPickedUrls(
        onFailure: BackupError,
        picker: () -> UIDocumentPickerViewController,
    ): Either<BackupError, List<NSURL>> = suspendCancellableCoroutine { continuation ->
        dispatch_async(dispatch_get_main_queue()) {
            if (!continuation.isActive) {
                return@dispatch_async
            }

            val delegate = BackupPickerDelegate { urls -> continuation.resume(urls.right()) }
            retainedDelegates += delegate

            Either.catch {
                val controller = picker()
                controller.delegate = delegate
                viewController.resolvePresenter().presentViewController(controller, true, null)
            }.onLeft {
                // Nothing was presented, so nothing is ever going to call back and the
                // delegate has no life left to be held through.
                retainedDelegates -= delegate
                continuation.resume(onFailure.left())
            }
        }
    }

    /**
     * A private copy of what the user chose, under a name of this app's choosing.
     *
     * The security scope is claimed even though `asCopy` already put the file inside the
     * sandbox: the picker is free to hand back a scoped url instead, and reading one
     * without claiming it fails. It is released in a `finally` because a scope left open is
     * a resource the system counts and does not reclaim.
     */
    private fun NSURL.copyIntoPrivateFile(): Either<BackupError, String> {
        val claimed = startAccessingSecurityScopedResource()
        try {
            val source = path ?: return BackupError.NOT_A_BACKUP.left()
            val directory = createPrivateDirectory() ?: return BackupError.NOT_A_BACKUP.left()
            return copyItem(source, "$directory/$CANDIDATE_NAME", BackupError.NOT_A_BACKUP)
        } finally {
            if (claimed) {
                stopAccessingSecurityScopedResource()
            }
        }
    }
}

/**
 * The delegates of pickers that are still on screen, and the only strong references to
 * them. Every read and write happens on the main queue, which is where a picker is
 * presented and where UIKit calls its delegate back.
 */
private val retainedDelegates = mutableSetOf<BackupPickerDelegate>()

private class BackupPickerDelegate(
    private val onPicked: (List<NSURL>) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) = deliver(didPickDocumentsAtURLs.filterIsInstance<NSURL>())

    override fun documentPickerWasCancelled(
        controller: UIDocumentPickerViewController,
    ) = deliver(emptyList())

    /**
     * Answers once and stops being retained, in that order. The order is what makes a
     * second callback harmless: the set no longer holds this delegate, so the removal
     * fails and there is nothing left to answer twice.
     */
    private fun deliver(urls: List<NSURL>) {
        if (!retainedDelegates.remove(this)) return
        onPicked(urls)
    }
}

/**
 * Somewhere the app may write and the system may reclaim, one directory per call so that
 * a name already used is never a name in the way — `copyItemAtPath` refuses a destination
 * that exists, and both directions write under a name they do not get to choose.
 */
@OptIn(ExperimentalForeignApi::class)
private fun createPrivateDirectory(): String? {
    val path = "${NSTemporaryDirectory().trimEnd('/')}/$PRIVATE_DIRECTORY/${NSUUID().UUIDString}"
    val created = NSFileManager.defaultManager.createDirectoryAtPath(
        path = path,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    return path.takeIf { created }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun copyItem(
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
private fun NSError?.toBackupError(otherwise: BackupError): BackupError =
    if (this?.code == NSFileWriteOutOfSpaceError) BackupError.NO_SPACE else otherwise

private const val PRIVATE_DIRECTORY = "finsight-backup"
private const val CANDIDATE_NAME = "candidate.db"
