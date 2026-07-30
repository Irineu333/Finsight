package com.neoutils.finsight.domain.model

/**
 * The exported document as text, already resolved: every string here is final, and nothing
 * downstream formats, translates or decides anything. The renderer prints it.
 *
 * A money cell holds **one line**, which makes the document a declared-degradation surface: a
 * figure of several terms reaches it through `CurrencyFormatter.formatSingleLine`, which marks
 * the line and says a term was left out. What the mark cannot do on paper is explain itself —
 * there is no footer to tap and no rates screen to reach — so each **family** of figures
 * carries a footnote beside it instead: the summary has its own, and so does each section.
 *
 * `null` is "nothing owed": a family whose every figure is exact prints no footnote, and the
 * document of a single-currency user is byte for byte the document it was.
 */
data class ReportLayout(
    val title: String,
    val generatedAtLabel: String,
    val context: ReportContext,
    val labels: ReportTableLabels,
    val summaryItems: List<ReportSummaryItem>,
    val summaryFootnote: String?,
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

    /** What this section's figures owe the reader, or `null` when they owe nothing. */
    val footnote: String?

    data class SpendingByCategory(
        val title: String,
        val items: List<CategoryItem>,
        override val footnote: String?,
    ) : ReportLayoutSection

    data class Transactions(
        val title: String,
        val groups: List<TransactionGroup>,
        override val footnote: String?,
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
