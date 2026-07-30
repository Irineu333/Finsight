package com.neoutils.finsight.ui.screen.report.viewer

import com.neoutils.finsight.extension.MoneyFigure
import com.neoutils.finsight.ui.model.CategorySpendingUi
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.ReportLayoutSection
import com.neoutils.finsight.domain.model.ReportTone
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionItem
import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.extension.Denomination
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import com.neoutils.finsight.ui.model.toTransactionUi
import com.neoutils.finsight.util.DateFormats
import com.neoutils.finsight.util.UiText
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format.DayOfWeekNames
import kotlinx.datetime.format.MonthNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `exportTone` and the amount are private, so the rule is exercised through
 * [toReportLayout], which is the surface that matters anyway.
 *
 * The negative branch of the tone used to be unreachable: it decided on `amount >= 0`
 * and the amount was always a magnitude, so every adjustment — including one that
 * shrank the user's net worth — was exported as positive.
 */
class ReportExportAdjustmentToneTest {

    private val formatter = CurrencyFormatter()

    private val card = Account(currency = "BRL", id = 1L, name = "Card", type = AccountType.LIABILITY)
    private val reconciliation = Account(currency = "BRL", id = 2L, name = "Reconciliation", type = AccountType.EQUITY)

    private val category = Category(
        id = 1L,
        name = "Adjustments",
        icon = CategoryLazyIcon(key = "other"),
        type = Category.Type.EXPENSE,
        createdAt = 0L,
    )

    private fun exportedItemOf(legAmountCents: Long): TransactionItem {
        val transaction = Transaction(
            id = 1L,
            title = "Adjustment",
            date = LocalDate(2026, 1, 1),
            entries = listOf(
                Entry(currency = "BRL", account = card, amount = legAmountCents),
                Entry(currency = "BRL", account = reconciliation, amount = -legAmountCents),
            ),
        )

        val content = ReportViewerUiState.Content(
            perspectiveLabel = "Card",
            perspectiveBadge = UiText.Raw("Card"),
            perspectiveIconKey = "other",
            stats = ReportViewerUiState.Stats.Invoice(
                openingDate = LocalDate(2026, 1, 1),
                closingDate = LocalDate(2026, 1, 31),
                expense = DisplayAmount.forcedNegative(0.0, Denomination.exact("BRL")),
                advancePayment = DisplayAmount.forcedPositive(0.0, Denomination.exact("BRL")),
                adjustment = DisplayAmount.explicitSign(100.0, Denomination.exact("BRL")),
                total = DisplayAmount.natural(100.0, Denomination.exact("BRL")),
            ),
            categorySpending = listOf(
                CategorySpendingUi(
                    category = category,
                    amount = MoneyFigure.of(DisplayAmount.natural(100.0, Denomination.exact("BRL"))),
                    percentage = 100.0,
                )
            ),
            categoryIncome = null,
            // The state carries display models, mapped under the card's perspective, as
            // the view model hands them over.
            transactions = mapOf(
                transaction.date to listOfNotNull(transaction.toTransactionUi(accountId = card.id)),
            ),
        )

        val layout = content.toReportLayout(
            strings = strings,
            dateFormats = DateFormats(MonthNames.ENGLISH_FULL, DayOfWeekNames.ENGLISH_FULL),
            formatter = formatter,
            perspectiveBadgeText = "Card",
        )

        return layout.sections
            .filterIsInstance<ReportLayoutSection.Transactions>()
            .single()
            .groups.single()
            .items.single()
    }

    @Test
    fun anAdjustmentThatRaisesTheDebtIsExportedNegative() {
        val item = exportedItemOf(legAmountCents = -10_000)

        assertEquals(ReportTone.NEGATIVE, item.tone)
        assertEquals("-" + formatter.format(100.0, "BRL"), item.amount)
    }

    @Test
    fun anAdjustmentThatLowersTheDebtIsExportedPositive() {
        val item = exportedItemOf(legAmountCents = 10_000)

        assertEquals(ReportTone.POSITIVE, item.tone)
        assertTrue(item.amount.startsWith("+"))
    }

    private val strings = ReportExportStrings(
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
        transactionTransfer = "Transfer",
        transactionPayment = "Payment",
        transactionBalanceAdjustment = "Balance adjustment",
        transactionInvoiceAdjustment = "Invoice adjustment",
        columnCategory = "Category",
        columnTransaction = "Transaction",
        columnAmount = "Amount",
        columnPercentage = "%",
    )
}
