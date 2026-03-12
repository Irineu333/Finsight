package com.neoutils.finsight.report

expect class PlatformReportOutputService() : ReportOutputService {
    override suspend fun export(document: ReportDocument): ReportOutputResult
    override suspend fun print(document: ReportDocument): ReportOutputResult
}
