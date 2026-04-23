package com.neoutils.finsight.ui.screen.report.service

import arrow.core.Either
import com.neoutils.finsight.domain.error.ReportOutputError
import com.neoutils.finsight.domain.model.ReportDocument
import com.neoutils.finsight.extension.PlatformContext

interface ReportPrintService {
    suspend fun print(
        document: ReportDocument,
        context: PlatformContext
    ): Either<ReportOutputError, Unit>
}
