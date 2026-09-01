package com.neoutils.finsight.backup.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.neoutils.finsight.domain.error.BackupError
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.extension.resolvePresenter
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Presents a picker and suspends until it answers, as the urls it was given or as an empty
 * list when the user closed it without choosing.
 *
 * It is here rather than beside one of its two callers because the problem it solves is
 * UIKit's and not any one picker's: [BackupFileService] raises two pickers over files and
 * [IosBackupFolder] raises one over a folder, and all three would otherwise carry their own
 * copy of the delegate-lifetime dance below.
 *
 * The delegate is put in [retainedDelegates] before the picker can reach it and taken out by
 * whichever callback ends the picker's life. That is the whole of why the set exists:
 * [UIDocumentPickerViewController.delegate] is a weak reference, so a delegate held by
 * nothing else is collectable the moment this function returns — the picker would come up,
 * the user would choose a file, and the callback would arrive at nothing. A cancelled
 * continuation deliberately does not release it: the picker is still on screen, still
 * holding the weak reference, and still going to call back. Answering a continuation that is
 * no longer listening costs nothing; being called through a collected delegate does not fail
 * so quietly.
 *
 * **What comes back is an `NSURL` and never anything derived from one.** A url the picker
 * grants carries its permission inside the object, out of band from the text of the address
 * (design D2), so the answer has to be the object itself for a caller to be able to use it.
 */
internal suspend fun PlatformContext.awaitPickedUrls(
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
