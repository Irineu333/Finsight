package com.neoutils.finsight

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.model.ReportPerspective
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.repository.dimensionBalancesInMonthByCurrency
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.domain.usecase.CalculateReportStatsUseCase
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.extension.DisplayAmount
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **The gate of the whole change: with one currency in use, nothing moved.**
 *
 * Every read answers a map of a single key, every number is exact, and the `≈` appears
 * nowhere — with no flag, no compatibility branch and no setting that turns it off. The
 * old behaviour is the particular case of the general one (design D9), which is what
 * makes the change safe for the user who will never see a second currency.
 *
 * **It is scoped to figures, not to layout.** Two layout changes reach the
 * single-currency user by a decision taken in the open — the reordering of the invoice
 * modals (design D24) and the removal of the hand-rolled `formatMoney` helpers — and a
 * gate that covered those would fail by construction, saying nothing about the numbers.
 *
 * The second half is the case the mark exists *not* to reach: every account in dollars
 * with the base in reais **and a rate on file**. Nothing is converted and nothing is
 * marked, because no read ever held more than one currency to reconcile.
 */
class SingleCurrencyGateTest {

    private val march = YearMonth(2026, 3)
    private val day = LocalDate(2026, 3, 15)

    @Test
    fun `every read answers one currency and every figure is exact`() = runApp(baseCurrency = "BRL") {
        val wallet = account("Nubank", currency = "BRL", isDefault = true)
        val food = category("Alimentação")
        val creditCard = card("Chase", currency = "BRL")
        val openInvoice = invoice(creditCard, march)

        income(wallet, amount = 1_000.0, date = day)
        expense(wallet, amount = 250.0, date = day, category = food)
        cardExpense(creditCard, openInvoice, amount = 120.0, date = day, category = food)

        assertEveryFigureIsExactlyOneCurrency(expected = "BRL", food = food, card = creditCard, invoice = openInvoice, wallet = wallet)
    }

    /**
     * The user with everything in dollars and a Brazilian device. The base resolved to
     * the real, he does not hold a cent in reais — and he sees dollars, exact and
     * unmarked, *even with the dollar rate on file*, because no read ever had two
     * currencies to reconcile.
     */
    @Test
    fun `a single currency other than the base is neither converted nor marked`() = runApp(baseCurrency = "BRL") {
        get<IExchangeRateRepository>().save(
            ExchangeRate(
                    currency = "USD",
                    counterCurrency = "BRL",
                    date = day,
                    rate = 5.5,
                    source = ExchangeRate.Source.USER,
                ),
        )

        val wallet = account("Chase", currency = "USD", isDefault = true)
        val food = category("Groceries")
        val creditCard = card("Amex", currency = "USD")
        val openInvoice = invoice(creditCard, march)

        income(wallet, amount = 1_000.0, date = day)
        expense(wallet, amount = 250.0, date = day, category = food)
        cardExpense(creditCard, openInvoice, amount = 120.0, date = day, category = food)

        assertEveryFigureIsExactlyOneCurrency(expected = "USD", food = food, card = creditCard, invoice = openInvoice, wallet = wallet)
    }

    /**
     * Every read this app makes across accounts, in one place: each answers a map of one
     * key, and each consolidated figure comes out exact, single-term and denominated in
     * the currency the accounts are actually in — never in the base "because it was at
     * hand".
     */
    private suspend fun AppLedgerHarness.assertEveryFigureIsExactlyOneCurrency(
        expected: String,
        food: com.neoutils.finsight.domain.model.Category,
        card: com.neoutils.finsight.domain.model.CreditCard,
        invoice: com.neoutils.finsight.domain.model.Invoice,
        wallet: com.neoutils.finsight.domain.model.Account,
    ) {
        val consolidate = get<ConsolidateMoneyUseCase>()

        // Dashboard — total balance, and the two natures the widgets sum.
        val balance = get<CalculateBalanceUseCase>()(march)
        val liabilities = entries.naturalBalanceUpToByCurrency(march, AccountType.LIABILITY)
        // Statement — the month's flows across every ASSET account.
        val assetFlows = entries.assetMonthFlowsByCurrency(march)
        // Cards — the month's flows across every card, and one invoice's owed.
        val cardFlows = entries.liabilityMonthFlowsByCurrency(march)
        val owed = entries.dimensionOwedByCurrency(requireNotNull(invoice.dimensionId))
        // Categories — a dimension's spending across accounts.
        val spending = entries.dimensionBalancesInMonthByCurrency(march, listOf(food.dimensionId))
        // Report — the most account-crossing figure of the app: the empty scope.
        val report = get<CalculateReportStatsUseCase>()(
            ReportPerspective.AccountPerspective(accountIds = emptyList()),
            LocalDate(2026, 3, 1),
            LocalDate(2026, 3, 31),
        )

        val perCurrency = listOf(
            "dashboard balance" to balance,
            "liabilities" to liabilities,
            "statement income" to assetFlows.income,
            "statement expense" to assetFlows.expense,
            "card expense" to cardFlows.expense,
            "invoice owed" to owed,
            "category spending" to spending.getValue(food.dimensionId),
            "report income" to report.income,
            "report balance" to report.balance,
        )

        perCurrency.forEach { (name, money) ->
            assertEquals(
                listOf(expected),
                money.currencies.toList(),
                "$name answered in more than the one currency in use",
            )
        }

        // And what the screens show out of them: single-term, exact, unmarked.
        perCurrency.forEach { (name, money) ->
            val figure = consolidate(money, on = day, policy = DisplayAmount::natural)
            assertTrue(figure.isSingleTerm, "$name reached a surface as more than one term")
            assertFalse(figure.isApproximate, "$name reached a surface marked approximate")
            assertEquals(expected, figure.terms.single().currency, "$name is denominated in the wrong currency")
        }

        // An account balance and an invoice's owed stay scalar, in their own currency.
        assertEquals(expected, requireNotNull(accounts.getAccountById(wallet.id)).currency)
        assertEquals(expected, card.currency)

        // A budget: with one currency among the accounts, its limit is in that currency
        // and its progress is exact — no new control, no mark.
        val budget = Budget(
            id = 1,
            title = "Alimentação",
            categories = listOf(food),
            iconKey = "food",
            amount = 500.0,
            currency = expected,
            createdAt = 0L,
        )
        val progress = get<CalculateBudgetProgressUseCase>()(budgets = listOf(budget), month = march).single()
        assertFalse(progress.isApproximate, "a single-currency budget reported an approximate progress")
    }
}
