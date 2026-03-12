package com.neoutils.finsight.report

data class ReportLayout(
    val title: String,
    val generatedAtLabel: String,
    val context: ReportContext,
    val labels: ReportTableLabels,
    val summaryItems: List<ReportSummaryItem>,
    val sections: List<ReportLayoutSection>,
)

data class ReportContext(
    val badge: String,
    val label: String,
    val period: String,
)

data class ReportTableLabels(
    val category: String,
    val transaction: String,
    val amount: String,
    val percentage: String,
)

data class ReportSummaryItem(
    val label: String,
    val value: String,
    val tone: ReportTone = ReportTone.NEUTRAL,
)

sealed interface ReportLayoutSection {
    data class SpendingByCategory(
        val title: String,
        val items: List<CategoryItem>,
    ) : ReportLayoutSection

    data class Transactions(
        val title: String,
        val groups: List<TransactionGroup>,
    ) : ReportLayoutSection
}

data class CategoryItem(
    val label: String,
    val amount: String,
    val percentage: String,
)

data class TransactionGroup(
    val dateLabel: String,
    val items: List<TransactionItem>,
)

data class TransactionItem(
    val title: String,
    val amount: String,
    val tone: ReportTone,
)

enum class ReportTone {
    POSITIVE,
    NEGATIVE,
    NEUTRAL,
}
