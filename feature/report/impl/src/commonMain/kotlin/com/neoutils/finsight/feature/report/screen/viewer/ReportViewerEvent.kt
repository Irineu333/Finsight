package com.neoutils.finsight.feature.report.screen.viewer

import com.neoutils.finsight.feature.report.model.ReportDocument

sealed class ReportViewerEvent {
    data class Print(val document: ReportDocument) : ReportViewerEvent()
    data class Share(val document: ReportDocument) : ReportViewerEvent()
}
