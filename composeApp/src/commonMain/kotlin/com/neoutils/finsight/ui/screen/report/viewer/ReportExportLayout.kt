package com.neoutils.finsight.ui.screen.report.viewer

import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.report.CategoryItem
import com.neoutils.finsight.report.ReportContext
import com.neoutils.finsight.report.ReportLayout
import com.neoutils.finsight.report.ReportLayoutSection
import com.neoutils.finsight.report.ReportSummaryItem
import com.neoutils.finsight.report.ReportTableLabels
import com.neoutils.finsight.report.ReportTone
import com.neoutils.finsight.report.TransactionGroup
import com.neoutils.finsight.report.TransactionItem
import com.neoutils.finsight.util.DateFormats
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt
import kotlin.time.Clock

data class ReportExportStrings(
    val title: String,
    val generatedAtPrefix: String,
    val summaryBalance: String,
    val summaryInitialBalance: String,
    val summaryIncome: String,
    val summaryExpense: String,
    val sectionSpendingByCategory: String,
    val sectionTransactions: String,
    val operationTransfer: String,
    val operationPayment: String,
    val operationBalanceAdjustment: String,
    val operationInvoiceAdjustment: String,
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
    val periodLabel = dateFormats.formatReportPeriod(startDate, endDate)

    val summaryItems = listOf(
        ReportSummaryItem(
            label = strings.summaryBalance,
            value = formatter.format(balance),
            tone = balance.toTone(),
        ),
        ReportSummaryItem(
            label = strings.summaryInitialBalance,
            value = formatter.format(initialBalance),
            tone = initialBalance.toTone(),
        ),
        ReportSummaryItem(
            label = strings.summaryIncome,
            value = "+${formatter.format(income)}",
            tone = ReportTone.POSITIVE,
        ),
        ReportSummaryItem(
            label = strings.summaryExpense,
            value = "-${formatter.format(expense)}",
            tone = ReportTone.NEGATIVE,
        ),
    )

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

        if (!transactions.isNullOrEmpty()) {
            add(
                ReportLayoutSection.Transactions(
                    title = strings.sectionTransactions,
                    groups = transactions.map { (date, operations) ->
                        TransactionGroup(
                            dateLabel = dateFormats.formatRelativeDate(date),
                            items = operations.map { operation ->
                                TransactionItem(
                                    title = operation.exportTitle(strings),
                                    amount = operation.exportAmount(formatter),
                                    tone = operation.exportTone(),
                                )
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

private fun Operation.exportTitle(strings: ReportExportStrings): String {
    return when {
        kind == Operation.Kind.PAYMENT -> strings.operationPayment
        kind == Operation.Kind.TRANSFER -> strings.operationTransfer
        type == Transaction.Type.ADJUSTMENT && target.isAccount -> strings.operationBalanceAdjustment
        type == Transaction.Type.ADJUSTMENT && target.isCreditCard -> strings.operationInvoiceAdjustment
        else -> label
    }
}

private fun Operation.exportAmount(formatter: CurrencyFormatter): String {
    return when (type) {
        Transaction.Type.ADJUSTMENT -> formatter.formatWithSign(amount)
        Transaction.Type.EXPENSE -> {
            if (kind == Operation.Kind.TRANSFER) {
                "-${formatter.format(amount)}"
            } else {
                formatter.format(amount)
            }
        }
        Transaction.Type.INCOME -> formatter.format(amount)
    }
}

private fun Operation.exportTone(): ReportTone {
    return when {
        kind == Operation.Kind.TRANSFER -> ReportTone.NEUTRAL
        type == Transaction.Type.INCOME -> ReportTone.POSITIVE
        type == Transaction.Type.EXPENSE -> ReportTone.NEGATIVE
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
