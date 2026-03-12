package com.neoutils.finsight.report

import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIMarkupTextPrintFormatter
import platform.UIKit.UIPrintInfo
import platform.UIKit.UIPrintInfoOutputType
import platform.UIKit.UIPrintInteractionController
import platform.UIKit.UIViewController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

class IosReportOutputService : ReportOutputService {

    override suspend fun export(document: ReportDocument): ReportOutputResult {
        if (document.format != ReportDocumentFormat.HTML) {
            return ReportOutputResult.Failure(ReportOutputError.UnsupportedFormat)
        }

        return runCatching {
            val presenter = findTopViewController()
                ?: return ReportOutputResult.Failure(ReportOutputError.Unknown)
            val shareController = UIActivityViewController(
                listOf(document.content.decodeToString()),
                null,
            )

            dispatch_async(dispatch_get_main_queue()) {
                presenter.presentViewController(
                    viewControllerToPresent = shareController,
                    animated = true,
                    completion = null,
                )
            }

            ReportOutputResult.Success()
        }.getOrElse {
            ReportOutputResult.Failure(ReportOutputError.Unknown)
        }
    }

    override suspend fun print(document: ReportDocument): ReportOutputResult {
        if (document.format != ReportDocumentFormat.HTML) {
            return ReportOutputResult.Failure(ReportOutputError.UnsupportedFormat)
        }

        return runCatching {
            val printInfo = UIPrintInfo.printInfo()
            printInfo.outputType = UIPrintInfoOutputType.UIPrintInfoOutputGeneral
            printInfo.jobName = document.fileNameWithoutExtension

            val printFormatter = UIMarkupTextPrintFormatter(document.content.decodeToString())

            val printController = UIPrintInteractionController.sharedPrintController()
            printController.printInfo = printInfo
            printController.printFormatter = printFormatter

            dispatch_async(dispatch_get_main_queue()) {
                printController.presentAnimated(true, completionHandler = null)
            }

            ReportOutputResult.Success()
        }.getOrElse {
            ReportOutputResult.Failure(ReportOutputError.Unknown)
        }
    }
}

private fun findTopViewController(): UIViewController? {
    var viewController = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return null
    while (true) {
        val presented = viewController.presentedViewController ?: break
        viewController = presented
    }
    return viewController
}
