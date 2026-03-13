package com.neoutils.finsight.report.service

import arrow.core.Either
import com.neoutils.finsight.domain.model.ReportLayout
import com.neoutils.finsight.report.ReportDocumentRenderer
import com.neoutils.finsight.report.ReportOutputError
import com.neoutils.finsight.report.ReportShareService

class ShareReportUseCase(
    private val renderer: ReportDocumentRenderer,
    private val shareService: ReportShareService,
) {
    suspend operator fun invoke(layout: ReportLayout): Either<ReportOutputError, Unit> {
        return shareService.share(renderer.render(layout))
    }
}
