package com.neoutils.finsight.feature.report.screen.config

import com.neoutils.finsight.feature.report.screen.ReportViewerParams
sealed class ReportConfigEvent {
    data class NavigateToViewer(val params: ReportViewerParams) : ReportConfigEvent()
}
