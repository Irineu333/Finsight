package com.neoutils.finsight

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.CurrencyAmount
import com.neoutils.finsight.domain.model.ReportPerspective
import com.neoutils.finsight.domain.repository.dimensionBalancesInMonthByCurrency
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.domain.usecase.CalculateReportStatsUseCase
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.extension.CurrencySymbols
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.format
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **The other half of the gate: not just the shape of the figures, the figures.**
 *
 * `SingleCurrencyGateTest` proves *form* — a map of one key, a single term, nothing
 * marked — and that is a real property, but it is one a wrong number satisfies perfectly.
 * The plan's claim was stronger than that: with one currency in use, the numbers are the
 * ones the app produced before any of this existed. Nothing held that half down, and it
 * is the gap a defect walked through once already (the percentage of 15.11 kept its shape
 * while losing its value).
 *
 * So this fixes a month and states the arithmetic **in full**, by hand. The expectations
 * below are not captured from a run: they are double entry done on paper over the three
 * movements in the fixture, which is what makes them able to disagree with the code. A
 * figure that changes here changes for a user, and the diff says by how much.
 *
 * The last two assertions are the ones no gate reached: what the screen actually renders.
 * A per-currency read that is right and a formatter that is wrong produce a correct map
 * and an incorrect app, and everything between `MoneyByCurrency` and the string was new
 * in this change.
 */
class SingleCurrencyGoldenTest {

    private val march = YearMonth(2026, 3)
    private val day = LocalDate(2026, 3, 15)

    /**
     * The ledger of the fixture, in full — three movements, one currency:
     *
     * ```
     * income      wallet +1.000,00        nominal INCOME  −1.000,00
     * expense     wallet   −250,00        nominal EXPENSE   +250,00   (dimension: food)
     * card spend  card     −120,00        nominal EXPENSE   +120,00   (dimension: food)
     * ```
     *
     * Everything asserted below is a sum over those six legs and nothing else.
     */
    @Test
    fun `a single-currency month produces exactly the figures it always did`() = withLocale(Locale("pt", "BR")) {
        runApp(baseCurrency = "BRL") {
            val wallet = account("Nubank", currency = "BRL", isDefault = true)
            val food = category("Alimentação")
            val creditCard = card("Chase", currency = "BRL")
            val openInvoice = invoice(creditCard, march)

            income(wallet, amount = 1_000.0, date = day)
            expense(wallet, amount = 250.0, date = day, category = food)
            cardExpense(creditCard, openInvoice, amount = 120.0, date = day, category = food)

            // One account: 1.000,00 in, 250,00 out.
            assertEquals(
                750.0,
                get<CalculateBalanceUseCase>().forAccount(wallet.id, march),
                "the account's own balance",
            )

            // Every ASSET account, which here is the same one.
            assertEquals(
                listOf(CurrencyAmount("BRL", 750.0)),
                get<CalculateBalanceUseCase>()(march).toList(),
                "the dashboard's total balance",
            )

            // Every LIABILITY account: the card was spent on and not paid. The sign is
            // the ledger's own — entries are debit-positive, and what a card owes sits
            // on the credit side — so a debt reads negative here and the widget that
            // shows it is what turns it into a magnitude.
            assertEquals(
                listOf(CurrencyAmount("BRL", -120.0)),
                entries.naturalBalanceUpToByCurrency(march, AccountType.LIABILITY).toList(),
                "what the cards owe",
            )

            // The statement's month: the income and the expense that touched an ASSET
            // account. The card's 120,00 is **not** here — it left no asset this month.
            val assetFlows = entries.assetMonthFlowsByCurrency(march)
            assertEquals(listOf(CurrencyAmount("BRL", 1_000.0)), assetFlows.income.toList(), "the statement's income")
            assertEquals(listOf(CurrencyAmount("BRL", 250.0)), assetFlows.expense.toList(), "the statement's expense")

            // The cards' month, which is where that 120,00 is instead.
            assertEquals(
                listOf(CurrencyAmount("BRL", 120.0)),
                entries.liabilityMonthFlowsByCurrency(march).expense.toList(),
                "the month's card expense",
            )

            // One invoice's owed.
            assertEquals(
                listOf(CurrencyAmount("BRL", 120.0)),
                entries.dimensionOwedByCurrency(requireNotNull(openInvoice.dimensionId)).toList(),
                "the open invoice's owed",
            )

            // A category spans accounts: it is the only figure here that adds a card
            // expense to a cash one, and it is the sum of both legs that carry it.
            assertEquals(
                listOf(CurrencyAmount("BRL", 370.0)),
                entries.dimensionBalancesInMonthByCurrency(march, listOf(food.dimensionId))
                    .getValue(food.dimensionId).toList(),
                "the category's spending",
            )

            // A budget of 500,00 against that spending: 370,00 spent, 130,00 left, 74%.
            val progress = get<CalculateBudgetProgressUseCase>()(
                budgets = listOf(
                    Budget(
                        id = 1,
                        title = "Alimentação",
                        categories = listOf(food),
                        iconKey = "food",
                        amount = 500.0,
                        currency = "BRL",
                        createdAt = 0L,
                    ),
                ),
                month = march,
            ).single()
            assertEquals(370.0, progress.spentAmount?.value, "the budget's spent")
            assertEquals(130.0, progress.remainingAmount?.value, "the budget's remaining")
            assertEquals<Float?>(0.74f, progress.progress, "the budget's progress")

            // The widest figure of the app: the empty scope, every account at once.
            val report = get<CalculateReportStatsUseCase>()(
                ReportPerspective.AccountPerspective(accountIds = emptyList()),
                LocalDate(2026, 3, 1),
                LocalDate(2026, 3, 31),
            )
            assertEquals(listOf(CurrencyAmount("BRL", 1_000.0)), report.income.toList(), "the report's income")
            assertEquals(listOf(CurrencyAmount("BRL", 250.0)), report.expense.toList(), "the report's expense")
            assertEquals(listOf(CurrencyAmount("BRL", 750.0)), report.balance.toList(), "the report's balance")

            // And the string. Everything between the per-currency read and this line was
            // new in this change, and no gate looked at it. The locale is fixed for the
            // whole test so that what is asserted is the app's formatting and not the
            // machine's.
            run {
                // The glyph comes from the currency table, so the table has to have been
                // read before a value can wear it. In the app that is the composition
                // root collecting the port; here it is awaiting the first row, and until
                // it arrives the worst case stands — the code itself, exactly as a
                // selector would show it.
                get<CurrencySymbols>().symbols.first { it.isNotEmpty() }

                val formatter = get<CurrencyFormatter>()
                val consolidate = get<ConsolidateMoneyUseCase>()

                val total = consolidate(get<CalculateBalanceUseCase>()(march), on = day, policy = DisplayAmount::natural)
                assertEquals("R$${NBSP}750,00", formatter.format(total.terms.single()), "the dashboard's total, as read")

                val spending = consolidate(
                    entries.dimensionBalancesInMonthByCurrency(march, listOf(food.dimensionId))
                        .getValue(food.dimensionId),
                    on = day,
                    policy = DisplayAmount::magnitude,
                )
                assertEquals("R$${NBSP}370,00", formatter.format(spending.terms.single()), "the category's spending, as read")
            }
        }
    }

    /**
     * The space a pt-BR currency format puts between symbol and figure is a
     * **non-breaking** one, and stating it here is the point of asserting a string at
     * all: it is exactly the kind of difference that a screenshot hides and a `trim()`
     * in a test would have swallowed.
     */
    private val NBSP = '\u00A0'

    private inline fun <T> withLocale(locale: Locale, block: () -> T): T {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        return try {
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }
}
