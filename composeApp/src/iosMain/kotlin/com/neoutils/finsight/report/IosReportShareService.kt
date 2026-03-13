@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.neoutils.finsight.report

import com.neoutils.finsight.domain.model.ReportDocument

class IosReportShareService : ReportShareService {

    override suspend fun share(document: ReportDocument): ReportOutputResult {
        throw RuntimeException("Not supported")
    }
}
