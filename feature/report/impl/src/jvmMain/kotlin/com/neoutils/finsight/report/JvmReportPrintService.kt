package com.neoutils.finsight.report

import arrow.core.Either
import arrow.core.left
import com.neoutils.finsight.domain.error.ReportOutputError
import com.neoutils.finsight.domain.model.ReportDocument
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.ui.screen.report.service.ReportPrintService
import java.awt.Desktop
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val AUTO_PRINT_SCRIPT = "<script>window.onload=()=>window.print()</script>"

class JvmReportPrintService : ReportPrintService {

    override suspend fun print(document: ReportDocument, context: PlatformContext): Either<ReportOutputError, Unit> = withContext(Dispatchers.IO) {
        if (document.format != ReportDocument.Format.HTML) {
            return@withContext ReportOutputError.UNSUPPORTED_FORMAT.left()
        }

        Either.catch {
            val html = document.content.decodeToString()
                .replace("</body>", "$AUTO_PRINT_SCRIPT</body>")

            val reportFile = File.createTempFile("finsight-report-", ".${document.format.fileExtension}")
            reportFile.writeText(html)
            reportFile.deleteOnExit()

            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(reportFile.toURI())
            }
        }.mapLeft { ReportOutputError.IO_ERROR }
    }
}
