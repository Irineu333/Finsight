package com.neoutils.finsight.report.service

import com.neoutils.finsight.domain.usecase.RenderHtmlReportUseCase
import com.neoutils.finsight.domain.model.ReportLayout
import com.neoutils.finsight.report.ReportOutputResult
import com.neoutils.finsight.report.ReportShareService

class ShareReportUseCase(
    private val renderReport: RenderHtmlReportUseCase,
    private val shareService: ReportShareService,
) {
    suspend operator fun invoke(layout: ReportLayout): ReportOutputResult {
        return shareService.share(renderReport(layout))
    }
}