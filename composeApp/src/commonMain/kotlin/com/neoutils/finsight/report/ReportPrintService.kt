package com.neoutils.finsight.report

import arrow.core.Either
import com.neoutils.finsight.domain.model.ReportDocument
import com.neoutils.finsight.extension.PlatformContext

interface ReportPrintService {
    suspend fun print(
        document: ReportDocument,
        context: PlatformContext
    ): Either<ReportOutputError, Unit>
}
