package com.neoutils.finsight.report

import java.awt.Desktop
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class JvmReportOutputService : ReportOutputService {

    override suspend fun export(document: ReportDocument): ReportOutputResult = withContext(Dispatchers.IO) {
        if (document.format != ReportDocumentFormat.HTML) {
            return@withContext ReportOutputResult.Failure(ReportOutputError.UnsupportedFormat)
        }

        runCatching {
            val reportFile = File.createTempFile("finsight-report-", ".${document.format.fileExtension}")
            reportFile.writeBytes(document.content)
            reportFile.deleteOnExit()
            if (Desktop.isDesktopSupported()) {
                val desktop = Desktop.getDesktop()
                when {
                    desktop.isSupported(Desktop.Action.BROWSE) -> desktop.browse(reportFile.toURI())
                    desktop.isSupported(Desktop.Action.OPEN) -> desktop.open(reportFile)
                }
            }
            ReportOutputResult.Success()
        }.getOrElse {
            ReportOutputResult.Failure(ReportOutputError.IoError)
        }
    }

    override suspend fun print(document: ReportDocument): ReportOutputResult = withContext(Dispatchers.IO) {
        if (document.format != ReportDocumentFormat.HTML) {
            return@withContext ReportOutputResult.Failure(ReportOutputError.UnsupportedFormat)
        }

        runCatching {
            val reportFile = File.createTempFile("finsight-report-", ".${document.format.fileExtension}")
            reportFile.writeBytes(document.content)
            reportFile.deleteOnExit()

            if (Desktop.isDesktopSupported()) {
                val desktop = Desktop.getDesktop()
                when {
                    desktop.isSupported(Desktop.Action.PRINT) -> desktop.print(reportFile)
                    desktop.isSupported(Desktop.Action.BROWSE) -> desktop.browse(reportFile.toURI())
                }
            }

            ReportOutputResult.Success()
        }.getOrElse {
            ReportOutputResult.Failure(ReportOutputError.IoError)
        }
    }
}
