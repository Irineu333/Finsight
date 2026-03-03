package com.neoutils.finsight.ui.screen.reports

sealed interface ReportDocument {
    val title: String
    val subtitle: String
    val fileName: String
    val mimeType: String
    val format: ReportFormat

    data class Pdf(
        override val title: String,
        override val subtitle: String,
        override val fileName: String,
        val highlights: List<ReportMetric>,
        val sections: List<ReportSection>,
        override val mimeType: String = "application/pdf",
        override val format: ReportFormat = ReportFormat.PDF,
    ) : ReportDocument

    data class Csv(
        override val title: String,
        override val subtitle: String,
        override val fileName: String,
        val content: String,
        override val mimeType: String = "text/csv",
        override val format: ReportFormat = ReportFormat.CSV,
    ) : ReportDocument
}

data class ReportMetric(
    val label: String,
    val value: String,
)

data class ReportSection(
    val title: String,
    val body: List<String> = emptyList(),
    val table: ReportTable? = null,
)

data class ReportTable(
    val columns: List<String>,
    val rows: List<List<String>>,
)
