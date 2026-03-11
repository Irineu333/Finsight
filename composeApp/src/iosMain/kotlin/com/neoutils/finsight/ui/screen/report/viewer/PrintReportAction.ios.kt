package com.neoutils.finsight.ui.screen.report.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIMarkupTextPrintFormatter
import platform.UIKit.UIPrintInfo
import platform.UIKit.UIPrintInfoOutputType
import platform.UIKit.UIPrintInteractionController

@Composable
internal actual fun rememberPrintReportAction(html: String, title: String): () -> Unit {
    return remember(html, title) {
        {
            val printInfo = UIPrintInfo.printInfo()
            printInfo.outputType = UIPrintInfoOutputType.UIPrintInfoOutputGeneral
            printInfo.jobName = title

            val formatter = UIMarkupTextPrintFormatter(markupText = html)

            val controller = UIPrintInteractionController.sharedPrintController()
            controller.printInfo = printInfo
            controller.printFormatter = formatter
            controller.presentAnimated(animated = true, completionHandler = null)
        }
    }
}
