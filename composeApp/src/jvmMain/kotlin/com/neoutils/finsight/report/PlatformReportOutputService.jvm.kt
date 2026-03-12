package com.neoutils.finsight.report

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class PlatformReportOutputService actual constructor() : ReportOutputService {
    actual override suspend fun export(document: ReportDocument): ReportOutputResult = withContext(Dispatchers.IO) {
        if (document.format != ReportDocumentFormat.HTML) {
            return@withContext ReportOutputResult.Failure(ReportOutputError.UnsupportedFormat)
        }

        runCatching {
            val reportsDir = File(System.getProperty("user.home"), ".finance/reports").apply {
                mkdirs()
            }
            val reportFile = File(reportsDir, document.fileName)
            reportFile.writeBytes(document.content)
            ReportOutputResult.Success(location = reportFile.absolutePath)
        }.getOrElse {
            ReportOutputResult.Failure(ReportOutputError.IoError)
        }
    }

    actual override suspend fun print(document: ReportDocument): ReportOutputResult {
        return ReportOutputResult.Failure(ReportOutputError.UnsupportedPrinting)
    }
}
