package com.neoutils.finsight.ui.screen.report.viewer

import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.ui.model.TransactionUi
import com.neoutils.finsight.ui.model.toTransactionUi
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
                value = formatter.format(s.balance),
                tone = s.balance.toTone(),
            ),
            ReportSummaryItem(
                label = strings.summaryOpeningBalance,
                value = formatter.format(s.openingBalance),
                tone = s.openingBalance.toTone(),
            ),
            ReportSummaryItem(
                label = strings.summaryIncome,
                value = "+${formatter.format(s.income)}",
                tone = ReportTone.POSITIVE,
            ),
            ReportSummaryItem(
                label = strings.summaryExpense,
                value = "-${formatter.format(s.expense)}",
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
                tone = s.total.toTone(),
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
                            label = item.category.name,
                            amount = formatter.format(item.amount),
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
                            label = item.category.name,
                            amount = formatter.format(item.amount),
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
                            items = transactions.mapNotNull { transaction ->
                                transaction.toTransactionUi()?.let { ui ->
                                    TransactionItem(
                                        title = ui.exportTitle(strings),
                                        amount = ui.exportAmount(formatter),
                                        tone = ui.exportTone(),
                                    )
                                }
                            },
                        )
                    },
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

private fun TransactionUi.exportAmount(formatter: CurrencyFormatter): String {
    return when (direction) {
        TransactionType.ADJUSTMENT -> formatter.formatWithSign(amount)
        TransactionType.EXPENSE -> {
            if (label == TransactionLabel.TRANSFER) {
                "-${formatter.format(amount)}"
            } else {
                formatter.format(amount)
            }
        }
        TransactionType.INCOME -> formatter.format(amount)
    }
}

private fun TransactionUi.exportTone(): ReportTone {
    return when {
        label == TransactionLabel.TRANSFER -> ReportTone.NEUTRAL
        direction == TransactionType.INCOME -> ReportTone.POSITIVE
        direction == TransactionType.EXPENSE -> ReportTone.NEGATIVE
        amount >= 0 -> ReportTone.POSITIVE
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
