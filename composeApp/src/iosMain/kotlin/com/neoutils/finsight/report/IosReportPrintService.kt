package com.neoutils.finsight.report

import arrow.core.Either
import arrow.core.left
import com.neoutils.finsight.domain.model.ReportDocument
import com.neoutils.finsight.extension.PlatformContext

class IosReportPrintService : ReportPrintService {

    override suspend fun print(document: ReportDocument, context: PlatformContext): Either<ReportOutputError, Unit> {
        return ReportOutputError.UNKNOWN.left()
    }
}
