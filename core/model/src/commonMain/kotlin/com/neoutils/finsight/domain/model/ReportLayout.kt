package com.neoutils.finsight.domain.model

data class ReportLayout(
    val title: String,
    val generatedAtLabel: String,
    val context: ReportContext,
    val labels: ReportTableLabels,
    val summaryItems: List<ReportSummaryItem>,
    val sections: List<ReportLayoutSection>,
    /**
     * What the document has to say about its own figures, or `null` when it has nothing —
     * which is every report of a user with a single currency.
     *
     * `money-display` requires an exported document to **declare** its limitation rather
     * than let it be inferred: the approximation mark travels inside each figure's text
     * and survives having no colour, but a mark alone does not say what it stands for, and
     * a printed page has no badge to open. So the document says it once, at the foot,
     * where a footnote belongs.
     *
     * It is **derived** from the figures the document holds, like the mark itself — a
     * report with nothing approximate in it cannot be given one, and one with something
     * approximate cannot lose it.
     */
    val footnote: String? = null,
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
