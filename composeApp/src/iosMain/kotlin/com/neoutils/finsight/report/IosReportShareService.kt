package com.neoutils.finsight.report

import arrow.core.Either
import arrow.core.left
import com.neoutils.finsight.domain.model.ReportDocument

class IosReportShareService : ReportShareService {

    override suspend fun share(document: ReportDocument): Either<ReportOutputError, Unit> {
        return ReportOutputError.UNKNOWN.left()
    }
}
