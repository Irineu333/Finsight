package com.neoutils.finsight.report

import arrow.core.Either
import com.neoutils.finsight.domain.model.ReportDocument

interface ReportShareService {
    suspend fun share(document: ReportDocument): Either<ReportOutputError, Unit>
}
