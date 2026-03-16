package com.neoutils.finsight.ui.screen.report.viewer

import com.neoutils.finsight.domain.model.ReportDocument

sealed class ReportViewerEvent {
    data class Print(val document: ReportDocument) : ReportViewerEvent()
    data class Share(val document: ReportDocument) : ReportViewerEvent()
}