package com.neoutils.finsight

import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.ICurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.usecase.ArchiveCurrencyUseCase
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.extension.DisplayAmount
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Archiving a currency is a rule about what is offered, and nothing else.**
 *
 * The registry's own tests already pin that archiving removes no row and no observation.
 * What they cannot reach is the other half of the sentence, because it lives below them:
 * an account in an archived currency goes on **taking entries** and goes on being
 * **consolidated**. That needs the real write boundary and the real reducer, so it is
 * asserted here, over the whole graph.
 *
 * It is the half most likely to be "fixed" by mistake — a veto on the archived currency
 * at the ledger's write boundary looks like a missing check and is actually a broken
 * module boundary (design D7). This test is what would fail if somebody added it.
 */
class ArchivedCurrencyStaysUsableTest {

    private val march = YearMonth(2026, 3)
    private val day = LocalDate(2026, 3, 15)

    @Test
    fun `an account in an archived currency goes on taking entries and being consolidated`() =
        runApp(baseCurrency = "BRL") {
            // Stated rather than assumed: `IBaseCurrencyRepository` is a Koin single, and
            // a single keeps its instance in the definition rather than in the container
            // (see AppLedgerHarness.close) — so a preference another test switched
            // outlives its harness, and this one consolidates against whatever it left.
            // Switching it here is what the app itself offers, and it costs one line.
            get<IBaseCurrencyRepository>().set("BRL")

            val nubank = account("Nubank", currency = "BRL", isDefault = true)
            val chase = account("Chase", currency = "USD")
            income(nubank, amount = 1_000.0, date = day)
            income(chase, amount = 100.0, date = day)

            get<IExchangeRateRepository>().save(
                ExchangeRate(
                    currency = "USD",
                    counterCurrency = "BRL",
                    date = day,
                    rate = 5.0,
                    source = ExchangeRate.Source.USER,
                ),
            )

            val balances = get<CalculateBalanceUseCase>()
            val consolidate = get<ConsolidateMoneyUseCase>()
            val before = consolidate(balances(march), on = day, policy = DisplayAmount::natural)
            assertEquals(1_500.0, before.terms.single().value, "1000 BRL + 100 USD at 5.00")

            get<ArchiveCurrencyUseCase>().archive("USD")
                .onLeft { error("archiving a currency that is not the base was refused: $it") }

            // The one thing archiving does: it stops being offered.
            assertTrue(
                get<ICurrencyRepository>().getOffered().none { it.code == "USD" },
                "an archived currency was still offered",
            )

            // And the things it does not do. The entry lands — the boundary has nothing
            // to refuse, because the ledger knows neither the offered set nor this flag.
            income(chase, amount = 50.0, date = day)
            assertEquals(150.0, balances.forAccount(chase.id, march))

            // The figure still reaches the base, through an observation archiving left
            // exactly where it was.
            val after = consolidate(balances(march), on = day, policy = DisplayAmount::natural)
            assertEquals(1_750.0, after.terms.single().value, "the 50 USD posted after archiving consolidates too")
            assertTrue(after.isApproximate, "a total holding two currencies came out exact")
        }
}
