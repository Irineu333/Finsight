package com.neoutils.finsight.report

import com.neoutils.finsight.domain.model.ReportDocument
import java.awt.Desktop
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val AUTO_PRINT_SCRIPT = "<script>window.onload=()=>window.print()</script>"

class JvmReportPrintService : ReportPrintService {

    override suspend fun print(document: ReportDocument): ReportOutputResult = withContext(Dispatchers.IO) {
        if (document.format != ReportDocument.Format.HTML) {
            return@withContext ReportOutputResult.Failure(ReportOutputError.UnsupportedFormat)
        }

        runCatching {
            val html = document.content.decodeToString()
                .replace("</body>", "$AUTO_PRINT_SCRIPT</body>")

            val reportFile = File.createTempFile("finsight-report-", ".${document.format.fileExtension}")
            reportFile.writeText(html)
            reportFile.deleteOnExit()

            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(reportFile.toURI())
            }

            ReportOutputResult.Success()
        }.getOrElse { ReportOutputResult.Failure(ReportOutputError.IoError) }
    }
}
