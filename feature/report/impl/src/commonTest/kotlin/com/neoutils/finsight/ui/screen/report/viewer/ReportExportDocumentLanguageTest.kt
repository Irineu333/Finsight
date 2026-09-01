package com.neoutils.finsight.ui.screen.report.viewer

import com.neoutils.finsight.extension.operationFormNames
import com.neoutils.finsight.di.commonModule
import com.neoutils.finsight.extension.ConsolidatedAmount
import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.ui.screen.report.render.HtmlReportDocumentRenderer
import com.neoutils.finsight.util.DateFormats
import com.neoutils.finsight.util.UiText
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.toLocalDateTime
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * **An exported document is written in one language, and says which.**
 *
 * The strings reach the export already resolved, so the document knows what language it
 * is in — a screen reader and a browser's translator do not, unless it declares it. And
 * the date it stamps on itself is a date like the others it prints: it belongs to the
 * same formatter, not to the machine format a `LocalDate` falls back to.
 */
class ReportExportDocumentLanguageTest {

    private val formatter: CurrencyFormatter = koinApplication { modules(commonModule, testSymbolsModule) }.koin.get()

    private val dateFormats = DateFormats(MonthNames.ENGLISH_FULL, DayOfWeekNames.ENGLISH_FULL)

    private fun figure() = ConsolidatedAmount(
        terms = listOf(DisplayAmount.magnitude(100.0, "BRL", isApproximate = false)),
        isApproximate = false,
        baseIndex = 0,
    )

    private fun layoutIn(languageTag: String) = ReportViewerUiState.Content(
        perspectiveLabel = "Wallet",
        perspectiveBadge = UiText.Raw("Accounts"),
        perspectiveIconKey = "other",
        stats = ReportViewerUiState.Stats.Account(
            startDate = LocalDate(2026, 1, 1),
            endDate = LocalDate(2026, 1, 31),
            openingBalance = figure(),
            income = figure(),
            expense = figure(),
            balance = figure(),
        ),
        categorySpending = null,
        categoryIncome = null,
        transactions = null,
    ).toReportLayout(
        strings = strings.copy(languageTag = languageTag),
        dateFormats = dateFormats,
        formatter = formatter,
        perspectiveBadgeText = "Accounts",
    )

    @Test
    fun `the document declares the language its strings were resolved in`() {
        val html = HtmlReportDocumentRenderer()
            .render(layoutIn("pt-BR"))
            .content
            .decodeToString()

        assertTrue(html.contains("<html lang=\"pt-BR\">"), html.take(80))
        assertFalse(html.contains("lang=\"en\""), "the renderer must not name a language of its own")
    }

    @Test
    fun `the generation date is written by the same formatter as the rest of the document`() {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        val label = layoutIn("en").generatedAtLabel

        assertEquals("Generated at: ${dateFormats.formatFullDate(today)}", label)
        assertFalse(today.toString() in label, "the ISO form is not a date this document prints")
    }

    private val strings = ReportExportStrings(
        languageTag = "en",
        title = "Report",
        generatedAtPrefix = "Generated at",
        summaryBalance = "Balance",
        summaryOpeningBalance = "Opening balance",
        summaryIncome = "Income",
        summaryExpense = "Expense",
        summaryInvoiceExpense = "Invoice expense",
        summaryInvoiceTotal = "Invoice total",
        summaryAdvancePayment = "Advance payment",
        sectionSpendingByCategory = "Spending by category",
        sectionIncomeByCategory = "Income by category",
        sectionTransactions = "Transactions",
        // The document names a line by the one rule; the export only resolves what it
        // answers, and the rule itself owns which cells exist.
        operationForms = operationFormNames().associateWith { "form" },
        uncategorized = "Uncategorized",
        columnCategory = "Category",
        columnTransaction = "Transaction",
        columnAmount = "Amount",
        columnPercentage = "%",
        footnote = "Approximate figures passed through a rate.",
    )
}
