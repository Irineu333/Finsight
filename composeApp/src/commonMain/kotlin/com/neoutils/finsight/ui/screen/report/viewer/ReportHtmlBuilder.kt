package com.neoutils.finsight.ui.screen.report.viewer

import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.util.DateFormats

internal data class ReportHtmlLabels(
    val balance: String,
    val initialBalance: String,
    val income: String,
    val expense: String,
    val spendingByCategory: String,
    val transactions: String,
)

internal fun buildReportHtml(
    state: ReportViewerUiState.Content,
    perspectiveBadgeText: String,
    formatter: CurrencyFormatter,
    dateFormats: DateFormats,
    labels: ReportHtmlLabels,
): String = buildString {
    appendLine(
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <style>
            * { margin: 0; padding: 0; box-sizing: border-box; }
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; padding: 32px; color: #1a1a1a; max-width: 800px; }
            .header-row { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; }
            h1 { font-size: 22px; font-weight: 700; }
            .badge { display: inline-block; background: rgba(25,118,210,0.12); color: #1976d2; padding: 3px 10px; border-radius: 99px; font-size: 12px; font-weight: 500; white-space: nowrap; flex-shrink: 0; }
            .period { color: #666; font-size: 13px; margin-bottom: 24px; }
            .summary { border: 1px solid #e0e0e0; border-radius: 8px; overflow: hidden; }
            .summary-balance { padding: 16px 20px; border-bottom: 1px solid #e0e0e0; }
            .summary-label { font-size: 12px; color: #666; margin-bottom: 4px; }
            .summary-main-value { font-size: 26px; font-weight: 700; }
            .summary-rows { padding: 4px 0; }
            .summary-row { display: flex; justify-content: space-between; padding: 8px 20px; }
            .summary-row + .summary-row { border-top: 1px solid #f0f0f0; }
            .row-label { font-size: 13px; color: #666; }
            .row-value { font-size: 13px; font-weight: 600; }
            .income { color: #2e7d32; }
            .expense { color: #c62828; }
            .section { margin-top: 28px; }
            h2 { font-size: 15px; font-weight: 600; margin-bottom: 12px; padding-bottom: 8px; border-bottom: 1px solid #e0e0e0; }
            table { width: 100%; border-collapse: collapse; }
            td { padding: 8px 4px; border-bottom: 1px solid #f5f5f5; font-size: 13px; }
            .amount-col { text-align: right; font-weight: 600; white-space: nowrap; }
            .pct-col { text-align: right; color: #888; font-size: 12px; width: 40px; }
            .date-header { padding: 12px 0 4px; color: #888; font-size: 12px; font-weight: 500; }
            @media print { body { padding: 16px; } }
          </style>
        </head>
        <body>
        """.trimIndent()
    )

    // Header
    appendLine("""<div class="header-row">""")
    appendLine("""<h1>${escapeHtml(state.perspectiveLabel)}</h1>""")
    appendLine("""<span class="badge">${escapeHtml(perspectiveBadgeText)}</span>""")
    appendLine("</div>")
    appendLine("""<p class="period">${dateFormats.formatReportPeriod(state.startDate, state.endDate)}</p>""")

    // Summary card
    appendLine("""<div class="summary">""")
    appendLine("""<div class="summary-balance">""")
    appendLine("""<div class="summary-label">${escapeHtml(labels.balance)}</div>""")
    val balanceClass = if (state.balance >= 0) "income" else "expense"
    appendLine("""<div class="summary-main-value $balanceClass">${formatter.format(state.balance)}</div>""")
    appendLine("</div>")
    appendLine("""<div class="summary-rows">""")
    val initialBalanceClass = if (state.initialBalance >= 0) "income" else "expense"
    appendLine(summaryRow(labels.initialBalance, formatter.format(state.initialBalance), initialBalanceClass))
    appendLine(summaryRow(labels.income, "+${formatter.format(state.income)}", "income"))
    appendLine(summaryRow(labels.expense, "-${formatter.format(state.expense)}", "expense"))
    appendLine("</div></div>")

    // Category spending
    state.categorySpending?.takeIf { it.isNotEmpty() }?.let { spending ->
        appendLine("""<div class="section">""")
        appendLine("""<h2>${escapeHtml(labels.spendingByCategory)}</h2>""")
        appendLine("<table><tbody>")
        spending.forEach { item ->
            val pct = "${(item.percentage * 100).toInt()}%"
            appendLine("<tr>")
            appendLine("""<td>${escapeHtml(item.category.name)}</td>""")
            appendLine("""<td class="pct-col">$pct</td>""")
            appendLine("""<td class="amount-col expense">${formatter.format(item.amount)}</td>""")
            appendLine("</tr>")
        }
        appendLine("</tbody></table></div>")
    }

    // Transactions
    state.transactions?.takeIf { it.isNotEmpty() }?.let { txMap ->
        appendLine("""<div class="section">""")
        appendLine("""<h2>${escapeHtml(labels.transactions)}</h2>""")
        txMap.forEach { (date, ops) ->
            appendLine("""<div class="date-header">${dateFormats.formatRelativeDate(date)}</div>""")
            appendLine("<table><tbody>")
            ops.forEach { op ->
                val amtClass = when {
                    op.type.isIncome -> "income"
                    op.type.isExpense -> "expense"
                    else -> ""
                }
                val sign = when {
                    op.type.isIncome -> "+"
                    op.type.isExpense -> "-"
                    else -> ""
                }
                appendLine("<tr>")
                appendLine("""<td>${escapeHtml(op.label)}</td>""")
                appendLine("""<td class="amount-col $amtClass">$sign${formatter.format(op.amount)}</td>""")
                appendLine("</tr>")
            }
            appendLine("</tbody></table>")
        }
        appendLine("</div>")
    }

    appendLine("</body></html>")
}

private fun summaryRow(label: String, value: String, valueClass: String) =
    """<div class="summary-row"><span class="row-label">${escapeHtml(label)}</span><span class="row-value $valueClass">$value</span></div>"""

private fun escapeHtml(text: String) = text
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
