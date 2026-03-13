package com.neoutils.finsight.report.service

import com.neoutils.finsight.domain.usecase.RenderHtmlReportUseCase
import com.neoutils.finsight.domain.model.ReportLayout
import com.neoutils.finsight.report.ReportOutputResult
import com.neoutils.finsight.report.ReportPrintService

class PrintReportUseCase(
    private val renderReport: RenderHtmlReportUseCase,
    private val printService: ReportPrintService,
) {
    suspend operator fun invoke(layout: ReportLayout): ReportOutputResult {
        return printService.print(renderReport(layout))
    }
}