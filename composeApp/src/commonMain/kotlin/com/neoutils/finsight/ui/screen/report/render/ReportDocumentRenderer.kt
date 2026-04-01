package com.neoutils.finsight.ui.screen.report.render

import com.neoutils.finsight.domain.model.ReportDocument
import com.neoutils.finsight.domain.model.ReportLayout

interface ReportDocumentRenderer {
    fun render(layout: ReportLayout): ReportDocument
}
