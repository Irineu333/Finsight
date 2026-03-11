package com.neoutils.finsight.ui.screen.report.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Desktop
import java.io.File

@Composable
internal actual fun rememberPrintReportAction(html: String, title: String): () -> Unit {
    return remember(html, title) {
        {
            val file = File.createTempFile("finsight-report-", ".html")
            file.writeText(html)
            file.deleteOnExit()
            Desktop.getDesktop().browse(file.toURI())
        }
    }
}
