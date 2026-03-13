package com.neoutils.finsight.report

import arrow.core.Either
import arrow.core.left
import com.neoutils.finsight.domain.model.ReportDocument

class IosReportPrintService : ReportPrintService {

    override suspend fun print(document: ReportDocument): Either<ReportOutputError, Unit> {
        return ReportOutputError.UNKNOWN.left()
    }
}
