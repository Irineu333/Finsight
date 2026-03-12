package com.neoutils.finsight.report

actual class PlatformReportOutputService actual constructor() : ReportOutputService {
    actual override suspend fun export(document: ReportDocument): ReportOutputResult {
        return ReportOutputResult.Failure(ReportOutputError.UnsupportedFormat)
    }

    actual override suspend fun print(document: ReportDocument): ReportOutputResult {
        return ReportOutputResult.Failure(ReportOutputError.UnsupportedPrinting)
    }
}
