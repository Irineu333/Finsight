package com.neoutils.finsight

import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.ui.model.toTransactionUi
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * **The other half of the gate — the one design D29 exists to keep from being cut.**
 *
 * `SingleCurrencyGateTest` says nothing changed for the user of one currency, and it is
 * exactly that configuration which makes "a figure is denominated by itself, never by
 * the base" **unobservable**: with every account in the base, showing the base and
 * showing the account's own currency produce the very same text. A balance wired to the
 * base by mistake passes it, and passes every review and every use — until somebody
 * creates a dollar account and sees it in reais, unconverted, with the wrong symbol.
 *
 * So this one holds an account and a card whose currency **differs** from the base, and
 * walks the four figures the ledger answers in one currency — account balance, statement
 * line, invoice owed, instalment — in the domain *and* through the surface that renders
 * them. A rate is on file throughout: not converting is a decision, not an accident of
 * having nothing to convert with.
 */
class ForeignAccountGateTest {

    private val march = YearMonth(2026, 3)
    private val day = LocalDate(2026, 3, 15)

    @Test
    fun `every figure of a foreign account is its own currency, and the base never appears`() =
        runApp(baseCurrency = "BRL") {
            get<IExchangeRateRepository>().save(
                ExchangeRate(
                    currency = "USD",
                    counterCurrency = "BRL",
                    date = day,
                    rate = 5.5,
                    source = ExchangeRate.Source.USER,
                ),
            )

            val chase = account("Chase", currency = "USD")
            val amex = card("Amex", currency = "USD")
            val openInvoice = invoice(amex, march)

            income(chase, amount = 1_000.0, date = day)
            expense(chase, amount = 250.0, date = day)
            cardExpense(amex, openInvoice, amount = 120.0, date = day)

            val formatter = get<CurrencyFormatter>()
            val brl = formatter.format(1.0, "BRL").filter { !it.isDigit() && it != ',' && it != '.' }.trim()

            // 1. Account balance — scalar, denominated by the account itself.
            val balance = get<CalculateBalanceUseCase>().forAccount(chase.id, march)
            assertEquals(750.0, balance, "the foreign account's balance is its own money, unconverted")
            val balanceText = formatter.format(balance, requireNotNull(accounts.getAccountById(chase.id)).currency)
            assertFalse(brl in balanceText, "the base currency's symbol reached a foreign account balance: $balanceText")

            // 2. Statement — every line of that account, as the list renders it.
            val statement = transactions.getAllTransactions()
                .mapNotNull { it.toTransactionUi(accountId = chase.id) }
            assertTrue(statement.isNotEmpty(), "the foreign account has no statement to check")
            statement.forEach { item ->
                assertEquals("USD", item.amount.currency, "a statement line fell back to the base currency")
                assertFalse(item.amount.isApproximate, "a statement line of a single account was marked approximate")
                assertFalse(brl in formatter.format(item.amount.value, item.amount.currency), "the base symbol reached a statement line")
            }

            // 3. Invoice owed — one currency by the card facade's own guarantee.
            val owed = entries.dimensionOwedByCurrency(requireNotNull(openInvoice.dimensionId))
            assertEquals(listOf("USD"), owed.currencies.toList(), "the invoice of a dollar card answered in another currency")
            val owedAmount = requireNotNull(owed.singleOrNull())
            assertEquals(120.0, owedAmount.value)
            assertFalse(
                brl in formatter.format(owedAmount.value, requireNotNull(amex.currency)),
                "the base symbol reached an invoice owed",
            )

            // 4. Instalment — denominated by the card, which never changes currency.
            val instalment = formatter.format(120.0 / 3, requireNotNull(amex.currency))
            assertFalse(brl in instalment, "the base symbol reached an instalment counter")

            // And the whole chart the user can see is in its own currency — nothing was
            // relabelled into the base on the way in.
            accounts.getAllAccounts().forEach {
                assertEquals("USD", it.currency, "an account was created in the base rather than in its own currency")
            }
        }
}
