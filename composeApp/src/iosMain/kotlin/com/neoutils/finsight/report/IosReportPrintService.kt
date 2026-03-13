package com.neoutils.finsight.report

import com.neoutils.finsight.domain.model.ReportDocument

class IosReportPrintService : ReportPrintService {

    override suspend fun print(document: ReportDocument): ReportOutputResult {
        throw RuntimeException("Not supported")
    }
}
