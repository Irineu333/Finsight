package com.neoutils.finsight.ui.screen.report.viewer

import com.neoutils.finsight.domain.model.ReportLayout

sealed class ReportViewerAction {
    data class ShareAsHtml(val layout: ReportLayout) : ReportViewerAction()
    data class Print(val layout: ReportLayout) : ReportViewerAction()
}
