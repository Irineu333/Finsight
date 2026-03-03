package com.neoutils.finsight.ui.screen.reports

interface ReportExportService {
    suspend fun exportAndShare(preview: GeneratedReportPreview): ReportExportResult
}

sealed interface ReportExportResult {
    data class Success(val fileName: String) : ReportExportResult
    data class Unsupported(val reason: String) : ReportExportResult
    data class Failure(val reason: String) : ReportExportResult
}

class UnsupportedReportExportService(
    private val reason: String,
) : ReportExportService {
    override suspend fun exportAndShare(preview: GeneratedReportPreview): ReportExportResult {
        return ReportExportResult.Unsupported(reason)
    }
}
