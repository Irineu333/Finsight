package com.neoutils.finsight.report

import arrow.core.Either
import arrow.core.right
import arrow.core.left
import com.neoutils.finsight.domain.model.ReportDocument
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.extension.popoverCenterRect
import com.neoutils.finsight.extension.resolvePresenter
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.popoverPresentationController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosReportShareService : ReportShareService {

    override suspend fun share(
        document: ReportDocument,
        context: PlatformContext
    ): Either<ReportOutputError, Unit> {
        if (document.format != ReportDocument.Format.HTML) {
            return ReportOutputError.UNSUPPORTED_FORMAT.left()
        }

        val reportUrl = Either.catch {
            writeReportToTemporaryFile(document)
        }.mapLeft {
            ReportOutputError.IO_ERROR
        }.getOrNull() ?: return ReportOutputError.IO_ERROR.left()

        return suspendCancellableCoroutine { continuation ->
            dispatch_async(dispatch_get_main_queue()) {
                if (!continuation.isActive) {
                    return@dispatch_async
                }

                Either.catch {
                    val presenter = context.viewController.resolvePresenter()
                    val activityController = UIActivityViewController(
                        activityItems = listOf(reportUrl),
                        applicationActivities = null,
                    )
                    activityController.popoverPresentationController?.let { popover ->
                        popover.sourceView = presenter.view
                        popover.sourceRect = presenter.view.popoverCenterRect()
                    }

                    presenter.presentViewController(activityController, true, null)
                }.fold(
                    ifLeft = { continuation.resume(ReportOutputError.UNKNOWN.left()) },
                    ifRight = { continuation.resume(Unit.right()) },
                )
            }
        }
    }

    private fun writeReportToTemporaryFile(document: ReportDocument): NSURL {
        val reportsPath = temporaryReportsDirectoryPath()
        val filePath = "$reportsPath/${NSUUID().UUIDString}-${document.fileName}"
        val didWrite = document.content.toNSData().writeToFile(filePath, true)
        check(didWrite)
        return NSURL.fileURLWithPath(filePath)
    }

    private fun temporaryReportsDirectoryPath(): String {
        val baseTempPath = NSTemporaryDirectory().trimEnd('/')
        val reportsPath = "$baseTempPath/finsight-reports"
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = reportsPath,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        return reportsPath
    }

    private fun ByteArray.toNSData(): NSData {
        if (isEmpty()) {
            return NSData()
        }
        return usePinned { pinned ->
            NSData.create(
                bytes = pinned.addressOf(0),
                length = size.toULong(),
            )
        }
    }
}
