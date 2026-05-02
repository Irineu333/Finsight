package com.neoutils.finsight.feature.report.screen.render

import com.neoutils.finsight.feature.report.model.ReportDocument
import com.neoutils.finsight.feature.report.model.ReportLayout
interface ReportDocumentRenderer {
    fun render(layout: ReportLayout): ReportDocument
}
