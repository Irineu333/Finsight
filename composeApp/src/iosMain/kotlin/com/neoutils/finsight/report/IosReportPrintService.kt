package com.neoutils.finsight.report

import arrow.core.Either
import arrow.core.right
import arrow.core.left
import com.neoutils.finsight.domain.model.ReportDocument
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.extension.popoverCenterRect
import com.neoutils.finsight.extension.resolvePresenter
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UIKit.UIDevice
import platform.UIKit.UIUserInterfaceIdiomPad
import platform.UIKit.UIPrintInfo
import platform.UIKit.UIPrintInfoOutputType
import platform.UIKit.UIPrintInteractionController
import platform.UIKit.UIMarkupTextPrintFormatter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

class IosReportPrintService : ReportPrintService {

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun print(document: ReportDocument, context: PlatformContext): Either<ReportOutputError, Unit> {
        if (document.format != ReportDocument.Format.HTML) {
            return ReportOutputError.UNSUPPORTED_FORMAT.left()
        }

        return suspendCancellableCoroutine { continuation ->
            dispatch_async(dispatch_get_main_queue()) {
                if (!continuation.isActive) {
                    return@dispatch_async
                }

                Either.catch {
                    val presenter = context.viewController.resolvePresenter()
                    val printController = checkNotNull(UIPrintInteractionController.sharedPrintController())
                    val printInfo = UIPrintInfo.printInfo().apply {
                        outputType = UIPrintInfoOutputType.UIPrintInfoOutputGeneral
                        jobName = document.fileNameWithoutExtension
                    }

                    printController.printInfo = printInfo
                    printController.printFormatter = UIMarkupTextPrintFormatter(
                        markupText = document.content.decodeToString(),
                    )
                    printController.showsNumberOfCopies = true

                    if (UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad) {
                        printController.presentFromRect(
                            rect = presenter.view.popoverCenterRect(),
                            inView = presenter.view,
                            animated = true,
                            completionHandler = null,
                        )
                    } else {
                        printController.presentAnimated(
                            animated = true,
                            completionHandler = null,
                        )
                    }
                }.fold(
                    ifLeft = { continuation.resume(ReportOutputError.UNKNOWN.left()) },
                    ifRight = { continuation.resume(Unit.right()) },
                )
            }
        }
    }
}
