package com.neoutils.finsight.report

interface ReportOutputService {
    suspend fun share(document: ReportDocument): ReportOutputResult
    suspend fun print(document: ReportDocument): ReportOutputResult
}

sealed class ReportOutputResult {
    data class Success(val location: String? = null) : ReportOutputResult()
    data class Failure(val error: ReportOutputError) : ReportOutputResult()
}

enum class ReportOutputError {
    UnsupportedFormat,
    UnsupportedPrinting,
    IoError,
    Unknown,
}
