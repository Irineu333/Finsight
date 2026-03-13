package com.neoutils.finsight.report.service

import arrow.core.Either
import com.neoutils.finsight.domain.model.ReportLayout
import com.neoutils.finsight.domain.usecase.RenderHtmlReportUseCase
import com.neoutils.finsight.report.ReportOutputError
import com.neoutils.finsight.report.ReportPrintService

class PrintReportUseCase(
    private val renderReport: RenderHtmlReportUseCase,
    private val printService: ReportPrintService,
) {
    suspend operator fun invoke(layout: ReportLayout): Either<ReportOutputError, Unit> {
        return printService.print(renderReport(layout))
    }
}
