package com.neoutils.finsight.feature.report.screen.service

import arrow.core.Either
import com.neoutils.finsight.feature.report.error.ReportOutputError
import com.neoutils.finsight.feature.report.model.ReportDocument
import com.neoutils.finsight.core.ui.extension.PlatformContext
interface ReportPrintService {
    suspend fun print(
        document: ReportDocument,
        context: PlatformContext
    ): Either<ReportOutputError, Unit>
}
