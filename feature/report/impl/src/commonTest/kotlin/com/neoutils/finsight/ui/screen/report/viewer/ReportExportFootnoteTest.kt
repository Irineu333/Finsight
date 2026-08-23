package com.neoutils.finsight.ui.screen.report.viewer

import com.neoutils.finsight.extension.operationFormNames
import com.neoutils.finsight.di.commonModule
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.SpendingSubject
import com.neoutils.finsight.domain.model.ReportLayoutSection
import com.neoutils.finsight.extension.ConsolidatedAmount
import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import com.neoutils.finsight.util.DateFormats
import com.neoutils.finsight.util.UiText
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **An exported document declares its limitation, and only when it has one.**
 *
 * `money-display` asks the export to say that a figure of it went through a rate. The mark
 * itself travels inside each figure's text and survives having no colour, which is half of
 * it — but a mark alone does not say what it stands for, and a printed page has no badge
 * to open. So the document says it once, at the foot.
 *
 * The other half is the silence: a report with nothing approximate in it must not carry
 * the sentence, or every single-currency user reads an explanation of something that never
 * happened to them. Both halves are here because only the pair is the rule.
 */
class ReportExportFootnoteTest {

    // The formatter's constructor is `internal` to `core/common`, so the only way to
    // reach one from outside is the binding the app itself uses — which resolves its
    // glyphs against the currency table, so the test has to provide one.
    private val formatter: CurrencyFormatter = koinApplication { modules(commonModule, testSymbolsModule) }.koin.get()

    private val category = Category(
        id = 1L,
        name = "Travel",
        icon = CategoryLazyIcon(key = "other"),
        type = Category.Type.EXPENSE,
        createdAt = 0L,
    )

    private fun exactFigure(currency: String) = ConsolidatedAmount(
        terms = listOf(DisplayAmount.magnitude(100.0, currency, isApproximate = false)),
        isApproximate = false,
        baseIndex = 0,
    )

    /** What a reduction that could not put everything in one currency answers. */
    private fun twoTermFigure() = ConsolidatedAmount(
        terms = listOf(
            DisplayAmount.magnitude(100.0, "BRL", isApproximate = false),
            DisplayAmount.magnitude(50.0, "USD", isApproximate = false),
        ),
        isApproximate = true,
        baseIndex = 0,
    )

    private fun layoutOf(spending: ConsolidatedAmount, balance: ConsolidatedAmount) =
        ReportViewerUiState.Content(
            perspectiveLabel = "Accounts",
            perspectiveBadge = UiText.Raw("Accounts"),
            perspectiveIconKey = "other",
            stats = ReportViewerUiState.Stats.Account(
                startDate = LocalDate(2026, 1, 1),
                endDate = LocalDate(2026, 1, 31),
                openingBalance = exactFigure("BRL"),
                income = exactFigure("BRL"),
                expense = exactFigure("BRL"),
                balance = balance,
            ),
            categorySpending = listOf(
                CategorySpending(subject = SpendingSubject.Categorized(category), amount = spending, percentage = 100.0),
            ),
            categoryIncome = null,
            transactions = null,
        ).toReportLayout(
            strings = strings,
            dateFormats = DateFormats(MonthNames.ENGLISH_FULL, DayOfWeekNames.ENGLISH_FULL),
            formatter = formatter,
            perspectiveBadgeText = "Accounts",
        )

    @Test
    fun `a report with nothing approximate carries no footnote`() {
        val layout = layoutOf(spending = exactFigure("BRL"), balance = exactFigure("BRL"))

        assertNull(layout.footnote)
    }

    @Test
    fun `a report whose category spending is approximate declares it`() {
        val layout = layoutOf(spending = twoTermFigure(), balance = exactFigure("BRL"))

        assertEquals(FOOTNOTE, layout.footnote)
    }

    @Test
    fun `a report whose summary is approximate declares it`() {
        val layout = layoutOf(spending = exactFigure("BRL"), balance = twoTermFigure())

        assertEquals(FOOTNOTE, layout.footnote)
    }

    /**
     * And the declaration is not a replacement for the terms: a table cell holds whatever
     * string it is given, so it is not one of D20's narrow surfaces and nothing is dropped
     * from it. The footnote says what the mark means; it does not stand in for a term.
     */
    @Test
    fun `both terms reach the exported figure`() {
        val layout = layoutOf(spending = twoTermFigure(), balance = exactFigure("BRL"))

        val amount = layout.sections
            .filterIsInstance<ReportLayoutSection.SpendingByCategory>()
            .single()
            .items.single()
            .amount

        assertTrue(formatter.format(100.0, "BRL") in amount, amount)
        assertTrue(formatter.format(50.0, "USD") in amount, amount)
    }

    /**
     * The order the export prints is the order the domain produced — the document
     * reorders nothing, which is what keeps it agreeing with the screen down to the
     * position of the unclassified line.
     */
    @Test
    fun `the export prints the breakdown in the order it received, unclassified last`() {
        val layout = ReportViewerUiState.Content(
            perspectiveLabel = "Accounts",
            perspectiveBadge = UiText.Raw("Accounts"),
            perspectiveIconKey = "other",
            stats = ReportViewerUiState.Stats.Account(
                startDate = LocalDate(2026, 1, 1),
                endDate = LocalDate(2026, 1, 31),
                openingBalance = exactFigure("BRL"),
                income = exactFigure("BRL"),
                expense = exactFigure("BRL"),
                balance = exactFigure("BRL"),
            ),
            categorySpending = listOf(
                CategorySpending(
                    subject = SpendingSubject.Categorized(category),
                    amount = exactFigure("BRL"),
                    percentage = 40.0,
                ),
                // Bigger than the category above it, and still where the domain put it.
                CategorySpending(
                    subject = SpendingSubject.Uncategorized,
                    amount = exactFigure("BRL"),
                    percentage = 60.0,
                ),
            ),
            categoryIncome = null,
            transactions = null,
        ).toReportLayout(
            strings = strings,
            dateFormats = DateFormats(MonthNames.ENGLISH_FULL, DayOfWeekNames.ENGLISH_FULL),
            formatter = formatter,
            perspectiveBadgeText = "Accounts",
        )

        val items = layout.sections
            .filterIsInstance<ReportLayoutSection.SpendingByCategory>()
            .single()
            .items

        assertEquals(listOf(category.name, UNCATEGORIZED), items.map { it.label })
        assertEquals("60.0%", items.last().percentage)
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
        uncategorized = UNCATEGORIZED,
        columnCategory = "Category",
        columnTransaction = "Transaction",
        columnAmount = "Amount",
        columnPercentage = "%",
        footnote = FOOTNOTE,
    )

    private companion object {
        const val FOOTNOTE = "Approximate figures passed through a rate."

        /** The label the screen resolved for the unclassified line, handed over as text. */
        const val UNCATEGORIZED = "Uncategorized"
    }
}
