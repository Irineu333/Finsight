package com.neoutils.finsight.ui.screen.report.viewer

import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.extension.ConsolidatedAmount
import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.extension.degradedTerm
import com.neoutils.finsight.extension.format
import com.neoutils.finsight.extension.formatTerms
import com.neoutils.finsight.ui.model.TransactionUi
import com.neoutils.finsight.domain.model.CategoryItem
import com.neoutils.finsight.domain.model.ReportContext
import com.neoutils.finsight.domain.model.ReportLayout
import com.neoutils.finsight.domain.model.ReportLayoutSection
import com.neoutils.finsight.domain.model.ReportSummaryItem
import com.neoutils.finsight.domain.model.ReportTableLabels
import com.neoutils.finsight.domain.model.ReportTone
import com.neoutils.finsight.domain.model.SpendingSubject
import com.neoutils.finsight.domain.model.TransactionGroup
import com.neoutils.finsight.domain.model.TransactionItem
import com.neoutils.finsight.util.DateFormats
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Clock

data class ReportExportStrings(
    /**
     * The BCP-47 tag of the language every string below was resolved in.
     *
     * It is read from the resources alongside them, so the document cannot declare one
     * language and be written in another.
     */
    val languageTag: String,
    val title: String,
    val generatedAtPrefix: String,
    val summaryBalance: String,
    val summaryOpeningBalance: String,
    val summaryIncome: String,
    val summaryExpense: String,
    val summaryInvoiceExpense: String,
    val summaryInvoiceTotal: String,
    val summaryAdvancePayment: String,
    val sectionSpendingByCategory: String,
    val sectionIncomeByCategory: String,
    val sectionTransactions: String,
    val transactionTransfer: String,
    val transactionPayment: String,
    val transactionBalanceAdjustment: String,
    val transactionInvoiceAdjustment: String,
    val transactionExpense: String,
    val transactionIncome: String,
    /**
     * The name of the unclassified line, resolved before the export runs — the document
     * is built outside the `@Composable` world, so every string it prints arrives here
     * already translated.
     */
    val uncategorized: String,
    val columnCategory: String,
    val columnTransaction: String,
    val columnAmount: String,
    val columnPercentage: String,
    val footnote: String,
)

fun ReportViewerUiState.Content.toReportLayout(
    strings: ReportExportStrings,
    dateFormats: DateFormats,
    formatter: CurrencyFormatter,
    perspectiveBadgeText: String,
): ReportLayout {
    val generatedAtDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    val periodLabel = when (val s = stats) {
        is ReportViewerUiState.Stats.Account -> dateFormats.formatReportPeriod(s.startDate, s.endDate)
        is ReportViewerUiState.Stats.Invoice -> dateFormats.formatReportPeriod(s.openingDate, s.closingDate)
    }

    val summaryItems = when (val s = stats) {
        is ReportViewerUiState.Stats.Account -> listOf(
            ReportSummaryItem(
                label = strings.summaryBalance,
                value = formatter.exportText(s.balance),
                tone = s.balance.toTone(),
            ),
            ReportSummaryItem(
                label = strings.summaryOpeningBalance,
                value = formatter.exportText(s.openingBalance),
                tone = s.openingBalance.toTone(),
            ),
            ReportSummaryItem(
                label = strings.summaryIncome,
                value = formatter.exportText(s.income),
                tone = ReportTone.POSITIVE,
            ),
            ReportSummaryItem(
                label = strings.summaryExpense,
                value = formatter.exportText(s.expense),
                tone = ReportTone.NEGATIVE,
            ),
        )

        is ReportViewerUiState.Stats.Invoice -> listOf(
            ReportSummaryItem(
                label = strings.summaryInvoiceExpense,
                value = formatter.format(s.expense),
                tone = ReportTone.NEGATIVE,
            ),
            ReportSummaryItem(
                label = strings.summaryInvoiceTotal,
                value = formatter.format(s.total),
                tone = s.total.value.toTone(),
            ),
            ReportSummaryItem(
                label = strings.summaryAdvancePayment,
                value = formatter.format(s.advancePayment),
                tone = ReportTone.POSITIVE,
            ),
        )
    }

    val sections = buildList {
        if (!categorySpending.isNullOrEmpty()) {
            add(
                ReportLayoutSection.SpendingByCategory(
                    title = strings.sectionSpendingByCategory,
                    items = categorySpending.map { item ->
                        CategoryItem(
                            label = item.subject.exportLabel(strings),
                            amount = formatter.exportText(item.amount),
                            percentage = item.percentage.toRoundedPercent(),
                        )
                    },
                )
            )
        }

        if (!categoryIncome.isNullOrEmpty()) {
            add(
                ReportLayoutSection.SpendingByCategory(
                    title = strings.sectionIncomeByCategory,
                    items = categoryIncome.map { item ->
                        CategoryItem(
                            label = item.subject.exportLabel(strings),
                            amount = formatter.exportText(item.amount),
                            percentage = item.percentage.toRoundedPercent(),
                        )
                    },
                )
            )
        }

        if (!transactions.isNullOrEmpty()) {
            add(
                ReportLayoutSection.Transactions(
                    title = strings.sectionTransactions,
                    groups = transactions.map { (date, transactions) ->
                        TransactionGroup(
                            dateLabel = dateFormats.formatRelativeDate(date),
                            items = transactions.map { ui ->
                                TransactionItem(
                                    title = ui.exportTitle(strings),
                                    amount = formatter.format(ui.amount),
                                    tone = ui.exportTone(),
                                )
                            },
                        )
                    },
                )
            )
        }
    }

    return ReportLayout(
        languageTag = strings.languageTag,
        title = strings.title,
        generatedAtLabel = "${strings.generatedAtPrefix}: ${dateFormats.formatFullDate(generatedAtDate)}",
        context = ReportContext(
            badge = perspectiveBadgeText,
            label = perspectiveLabel,
            period = periodLabel,
        ),
        labels = ReportTableLabels(
            category = strings.columnCategory,
            transaction = strings.columnTransaction,
            amount = strings.columnAmount,
            percentage = strings.columnPercentage,
        ),
        summaryItems = summaryItems,
        sections = sections,
        // Derived, never declared: the document says what its mark means exactly when it
        // carries one. A single-currency report has no approximate figure in it and gets
        // no footnote at all — the same rule, and the same silence, as everywhere else.
        footnote = strings.footnote.takeIf { approximateFigures().any { figure -> figure.isApproximate } },
    )
}

/**
 * Every figure of the document that could be approximate.
 *
 * The invoice perspective is absent on purpose: an invoice report is scoped to one card,
 * so each of its lines is a `DisplayAmount` denominated by that card's account and no
 * reduction ever took place (design D17).
 */
private fun ReportViewerUiState.Content.approximateFigures(): List<ConsolidatedAmount> = buildList {
    (stats as? ReportViewerUiState.Stats.Account)?.let {
        add(it.openingBalance)
        add(it.income)
        add(it.expense)
        add(it.balance)
    }
    categorySpending?.forEach { add(it.amount) }
    categoryIncome?.forEach { add(it.amount) }
}

/**
 * The subject's name in the document. The order the items come in is the domain's and
 * is printed as received — the export reorders nothing, which is what keeps it agreeing
 * with the screen down to the position of the unclassified line.
 */
private fun SpendingSubject.exportLabel(strings: ReportExportStrings): String = when (this) {
    is SpendingSubject.Categorized -> category.name
    SpendingSubject.Uncategorized -> strings.uncategorized
}

/**
 * The name of the operation in the document: its title, then its category, then its form.
 *
 * The same precedence the list reads by — [TransactionUi.title] answers the first two
 * links, and the third is the document's, which names each line on its own exactly as a
 * list item does. An exported report that disagreed with the screen it was exported from
 * would be the same operation under two names.
 */
private fun TransactionUi.exportTitle(strings: ReportExportStrings): String {
    return title ?: when (label) {
        TransactionLabel.PAYMENT -> strings.transactionPayment
        TransactionLabel.TRANSFER -> strings.transactionTransfer
        TransactionLabel.ADJUSTMENT -> if (isCardTarget) {
            strings.transactionInvoiceAdjustment
        } else {
            strings.transactionBalanceAdjustment
        }

        TransactionLabel.EXPENSE -> strings.transactionExpense
        TransactionLabel.INCOME -> strings.transactionIncome
    }
}

/**
 * The tone reads the sign off the very value that will be printed, so text and color
 * cannot disagree. Before, `amount` was always a magnitude and an adjustment could only
 * ever land on [ReportTone.POSITIVE] — the negative branch was unreachable.
 */
private fun TransactionUi.exportTone(): ReportTone {
    return when {
        label == TransactionLabel.TRANSFER -> ReportTone.NEUTRAL
        direction == TransactionType.INCOME -> ReportTone.POSITIVE
        direction == TransactionType.EXPENSE -> ReportTone.NEGATIVE
        amount.value >= 0 -> ReportTone.POSITIVE
        else -> ReportTone.NEGATIVE
    }
}

/**
 * A share with no answer renders as a dash, and it survives having no colour — which is
 * what design D20 asks of an exported document. `0%` would be a claim the app cannot
 * make: no rate reaches that category's currency, so its share of the total is unknown
 * rather than nil.
 */
private fun Double?.toRoundedPercent(): String {
    if (this == null) return "—"
    return roundedPercent()
}

private fun Double.roundedPercent(): String {
    val rounded = (this * 10).roundToInt() / 10.0
    return "$rounded%"
}

/**
 * The exported document stores text and not a figure, so a figure of more than one term
 * is written out whole, in the order the terms read.
 *
 * **Whole, and not degraded to the base term.** D20 lists the exported document among the
 * surfaces of fixed width or of a grammar of their own, and a table cell is neither: it
 * holds whatever string it is given. So the rule that applies here is the other one — *a
 * surface that can show more than one term shows them all* — and nothing is dropped for a
 * reader to not notice. What the document owes on top of that is saying what the mark
 * means, which is [ReportLayout.footnote].
 */
private fun CurrencyFormatter.exportText(figure: ConsolidatedAmount): String =
    formatTerms(figure).joinToString(" ")

/**
 * The tone of a figure is the tone of the term it was reduced into — the one a surface
 * too narrow for the rest would keep.
 */
private fun ConsolidatedAmount.toTone(): ReportTone = degradedTerm().value.toTone()

private fun Double.toTone(): ReportTone {
    return when {
        this > 0 -> ReportTone.POSITIVE
        this < 0 -> ReportTone.NEGATIVE
        else -> ReportTone.NEUTRAL
    }
}
