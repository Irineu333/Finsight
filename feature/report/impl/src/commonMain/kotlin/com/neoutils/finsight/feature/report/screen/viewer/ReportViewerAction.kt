package com.neoutils.finsight.feature.report.screen.viewer

import com.neoutils.finsight.feature.report.model.ReportLayout

sealed class ReportViewerAction {
    data class Share(val layout: ReportLayout) : ReportViewerAction()
    data class Print(val layout: ReportLayout) : ReportViewerAction()
}
