package com.neoutils.finsight.ui.screen.report.viewer

import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.extension.AppliedRate
import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.extension.MoneyFigure
import com.neoutils.finsight.extension.appliedRatesOf
import com.neoutils.finsight.extension.explanationIsOwed
import com.neoutils.finsight.extension.formatSingleLine
import com.neoutils.finsight.ui.model.TransactionUi
import com.neoutils.finsight.domain.model.CategoryItem
import com.neoutils.finsight.domain.model.ReportContext
import com.neoutils.finsight.domain.model.ReportLayout
import com.neoutils.finsight.domain.model.ReportLayoutSection
import com.neoutils.finsight.domain.model.ReportSummaryItem
import com.neoutils.finsight.domain.model.ReportTableLabels
import com.neoutils.finsight.domain.model.ReportTone
import com.neoutils.finsight.domain.model.TransactionGroup
import com.neoutils.finsight.domain.model.TransactionItem
import com.neoutils.finsight.util.DateFormats
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Clock

data class ReportExportStrings(
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
    val columnCategory: String,
    val columnTransaction: String,
    val columnAmount: String,
    val columnPercentage: String,
    /**
     * What the marks mean, printed under a family of figures that carries one. The document
     * has no footer to tap and no rates screen to reach, so the explanation the app gives by
     * navigation is given here by text.
     */
    val approximationConverted: String,
    val approximationUnreached: String,
    /**
     * One quote, spelled out. It is a lambda because the rates are only known here, outside a
     * composition, while the string and the date format belong to the UI — so the UI closes
     * over both and hands down a function rather than letting [AppliedRate] into the layout.
     */
    val approximationRate: (AppliedRate) -> String,
)

/**
 * What a family of figures owes its reader, or `null` when it owes nothing.
 *
 * The condition is [explanationIsOwed] and not `isApproximate`: a cell shows one line, so
 * `formatSingleLine` **forces** the mark on a figure of several exact terms — asking only
 * about approximation would print a mark this note never explains.
 *
 * With several terms and no rate at all there is nothing to reveal and the note still prints:
 * the elision is what is being explained.
 */
private fun footnoteOf(figures: List<MoneyFigure>, strings: ReportExportStrings): String? {
    if (!explanationIsOwed(figures)) return null

    val rates = appliedRatesOf(figures)
    val explanation = if (rates.isEmpty()) strings.approximationUnreached else strings.approximationConverted
    return (listOf(explanation) + rates.map(strings.approximationRate)).joinToString(" ")
}

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

    // The value of a cell goes through the single-line declaration: the document's grammar is
    // one term per line, so leaving a term out is stated here rather than by the layout.
    val summaryItems = when (val s = stats) {
        is ReportViewerUiState.Stats.Account -> listOf(
            ReportSummaryItem(
                label = strings.summaryBalance,
                value = formatter.formatSingleLine(s.balance.figure),
                tone = s.balance.comparable.toTone(),
            ),
            ReportSummaryItem(
                label = strings.summaryOpeningBalance,
                value = formatter.formatSingleLine(s.openingBalance.figure),
                tone = s.openingBalance.comparable.toTone(),
            ),
            ReportSummaryItem(
                label = strings.summaryIncome,
                value = formatter.formatSingleLine(s.income.figure),
                tone = ReportTone.POSITIVE,
            ),
            ReportSummaryItem(
                label = strings.summaryExpense,
                value = formatter.formatSingleLine(s.expense.figure),
                tone = ReportTone.NEGATIVE,
            ),
        )

        is ReportViewerUiState.Stats.Invoice -> listOf(
            ReportSummaryItem(
                label = strings.summaryInvoiceExpense,
                value = formatter.formatSingleLine(s.expense.figure),
                tone = ReportTone.NEGATIVE,
            ),
            ReportSummaryItem(
                label = strings.summaryInvoiceTotal,
                value = formatter.formatSingleLine(s.total.figure),
                tone = s.total.comparable.toTone(),
            ),
            ReportSummaryItem(
                label = strings.summaryAdvancePayment,
                value = formatter.formatSingleLine(s.advancePayment.figure),
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
                            label = item.category.name,
                            amount = formatter.formatSingleLine(item.amount),
                            percentage = item.percentage.toRoundedPercent(),
                        )
                    },
                    footnote = footnoteOf(categorySpending.map { it.amount }, strings),
                )
            )
        }

        if (!categoryIncome.isNullOrEmpty()) {
            add(
                ReportLayoutSection.SpendingByCategory(
                    title = strings.sectionIncomeByCategory,
                    items = categoryIncome.map { item ->
                        CategoryItem(
                            label = item.category.name,
                            amount = formatter.formatSingleLine(item.amount),
                            percentage = item.percentage.toRoundedPercent(),
                        )
                    },
                    footnote = footnoteOf(categoryIncome.map { it.amount }, strings),
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
                                    amount = formatter.formatSingleLine(MoneyFigure.of(ui.amount)),
                                    tone = ui.exportTone(),
                                )
                            },
                        )
                    },
                    // A statement line is denominated by the account its leg posted in, so it
                    // is single-term by construction and this note never prints. It is asked
                    // anyway, of the figures themselves, rather than assumed away.
                    footnote = footnoteOf(
                        transactions.values.flatten().map { MoneyFigure.of(it.amount) },
                        strings,
                    ),
                )
            )
        }
    }

    return ReportLayout(
        title = strings.title,
        generatedAtLabel = "${strings.generatedAtPrefix}: ${generatedAtDate.toString()}",
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
        summaryFootnote = footnoteOf(stats.figures, strings),
        sections = sections,
    )
}

private fun TransactionUi.exportTitle(strings: ReportExportStrings): String {
    return when {
        label == TransactionLabel.PAYMENT -> strings.transactionPayment
        label == TransactionLabel.TRANSFER -> strings.transactionTransfer
        label == TransactionLabel.ADJUSTMENT && !isCardTarget -> strings.transactionBalanceAdjustment
        label == TransactionLabel.ADJUSTMENT && isCardTarget -> strings.transactionInvoiceAdjustment
        else -> title
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

private fun Double.toRoundedPercent(): String {
    val rounded = (this * 10).roundToInt() / 10.0
    return "$rounded%"
}

private fun Double.toTone(): ReportTone {
    return when {
        this > 0 -> ReportTone.POSITIVE
        this < 0 -> ReportTone.NEGATIVE
        else -> ReportTone.NEUTRAL
    }
}
