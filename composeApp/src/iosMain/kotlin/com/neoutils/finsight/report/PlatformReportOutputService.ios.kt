@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.neoutils.finsight.report

class IosReportOutputService : ReportOutputService {

    override suspend fun share(document: ReportDocument): ReportOutputResult {
        throw RuntimeException("Not supported")
    }

    override suspend fun print(document: ReportDocument): ReportOutputResult {
        throw RuntimeException("Not supported")
    }
}
