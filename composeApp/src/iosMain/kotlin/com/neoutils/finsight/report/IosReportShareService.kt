package com.neoutils.finsight.report

import arrow.core.Either
import arrow.core.left
import com.neoutils.finsight.domain.model.ReportDocument
import com.neoutils.finsight.extension.PlatformContext

class IosReportShareService : ReportShareService {

    override suspend fun share(document: ReportDocument, context: PlatformContext): Either<ReportOutputError, Unit> {
        return ReportOutputError.UNKNOWN.left()
    }
}
