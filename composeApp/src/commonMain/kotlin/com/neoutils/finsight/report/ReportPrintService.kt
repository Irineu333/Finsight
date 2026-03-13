package com.neoutils.finsight.report

import arrow.core.Either
import com.neoutils.finsight.domain.model.ReportDocument

interface ReportPrintService {
    suspend fun print(document: ReportDocument): Either<ReportOutputError, Unit>
}
