package com.neoutils.finsight.report

import com.neoutils.finsight.domain.model.ReportDocument

interface ReportShareService {
    suspend fun share(document: ReportDocument): ReportOutputResult
}

interface ReportPrintService {
    suspend fun print(document: ReportDocument): ReportOutputResult
}

sealed class ReportOutputResult {
    data class Success(val location: String? = null) : ReportOutputResult()
    data class Failure(val error: ReportOutputError) : ReportOutputResult()
}

enum class ReportOutputError {
    UnsupportedFormat,
    IoError,
    Unknown,
}
