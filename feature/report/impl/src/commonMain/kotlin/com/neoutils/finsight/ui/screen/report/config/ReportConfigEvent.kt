package com.neoutils.finsight.ui.screen.report.config

import com.neoutils.finsight.ui.screen.report.ReportViewerParams

sealed class ReportConfigEvent {
    data class NavigateToViewer(val params: ReportViewerParams) : ReportConfigEvent()
}
