package com.neoutils.finsight

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where conversion is allowed to happen, asserted over the sources — because the boundary is
 * a fact about the whole app and no single module can state it.
 *
 * Four things are pinned, and each one is a rule the compiler cannot reach:
 *
 * 1. the **ledger** knows nothing of rates or of a base currency. It is enforced by the module
 *    graph already — the consolidation layer depends on the ledger and not the other way round
 *    — but the graph would let a rate arrive as a *parameter*, and the sentence the ledger
 *    sustains is that every figure is `Σ entries`;
 * 2. **exactness is derived in one place.** Only the consolidation layer denominates a figure
 *    as approximate; anywhere else that would be a screen deciding, by hand, that its own
 *    number is an approximation — which is how the mark goes missing;
 * 3. **nothing converts in a screen, a ViewModel or a UI model.** One implementation reduces a
 *    per-currency result, and every consumer goes through it;
 * 4. **no figure samples the base currency.** It is an observable preference, and a consumer
 *    that read it once instead of following it would render a card that quietly stops
 *    reacting — a failure no behaviour test catches while the v1 does not offer changing it.
 */
class ConsolidationBoundaryTest {

    /** The one place a rate may be applied to a **figure**. */
    private val consolidationLayer =
        "core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/ConsolidateFigureUseCase.kt"

    /**
     * The sites allowed to multiply by a rate at all: the consolidation, and the suggestion of
     * the second amount of a cross-currency operation.
     *
     * The second is not a figure and never becomes one — it is a value offered *into a field*,
     * which the user overwrites at will and which the ledger then records as two typed
     * amounts. Keeping it out of the consolidation is the point: a figure is reduced in one
     * place, and a form suggesting a number is not reducing anything.
     */
    private val rateAppliers = listOf(
        consolidationLayer,
        "core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/SuggestConvertedAmountUseCase.kt",
    )

    @Test
    fun `the ledger names no rate and no base-currency preference`() {
        val offenders = sourcesUnder("core/ledger")
            .filter { file -> RATE_REFERENCE.containsMatchIn(file.code) }
            .map { it.path }

        assertEquals(
            emptyList(),
            offenders,
            "A ledger read that multiplied entries by a rate would stop being Σ entries.",
        )
    }

    @Test
    fun `only the consolidation layer may call a figure approximate`() {
        val offenders = sourcesUnder("core", "feature", "app")
            .filterNot { it.path == consolidationLayer }
            .filter { it.text.contains("Denomination.approximate") }
            .map { it.path }

        assertEquals(
            emptyList(),
            offenders,
            "Exactness is derived from the per-currency result and the rates available; " +
                "marking it by hand is the failure the restriction exists to prevent.",
        )
    }

    @Test
    fun `no screen, ViewModel or UI model applies a rate`() {
        val offenders = sourcesUnder("core", "feature", "app")
            .filterNot { it.path in rateAppliers }
            .filter { file -> RATE_APPLICATION.containsMatchIn(file.code) }
            .map { it.path }

        assertEquals(
            emptyList(),
            offenders,
            "Reducing a per-currency result has exactly one implementation, and every " +
                "consumer of a consolidated figure goes through it.",
        )
    }

    /**
     * The base currency reaches a figure as a **flow**, never as a snapshot.
     *
     * `current()` is a legitimate method with a narrow use — a caller that *decides* something
     * once, like which currency to pre-select for a new account — and an illegitimate one that
     * looks identical: a figure resolving the preference inside itself. Both compile, and in a
     * v1 that does not offer changing the base, both behave the same; the difference only
     * shows the day it can change, when half a card follows and half does not.
     *
     * So the readers are named. Adding a site here is a claim that it decides rather than
     * renders, and the claim is the point.
     */
    @Test
    fun `only a decision reads the base currency, and a figure follows it`() {
        val offenders = sourcesUnder("core", "feature", "app")
            .filterNot { file -> baseCurrencyDeciders.any { file.path.endsWith(it) } }
            .filter { file -> BASE_CURRENCY_SNAPSHOT.containsMatchIn(file.code) }
            .map { it.path }

        assertEquals(
            emptyList(),
            offenders,
            "A consolidated figure takes the base as an argument, resolved from " +
                "IBaseCurrencyRepository.observe() in the flow that produces it — so one " +
                "emission has exactly one base, and forgetting to follow the preference is a " +
                "compile error rather than a screen that stops reacting.",
        )
    }

    /**
     * The sites that legitimately read the base once, **none of which render a figure**: the
     * settings screens, whose subject *is* the preference; the two paths that pre-select the
     * currency of an account or card being created (design D28); and the dashboard preview
     * factory, whose numbers are fabricated examples rather than reads of the ledger.
     */
    private val baseCurrencyDeciders = listOf(
        "feature/settings/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/settings/SettingsViewModel.kt",
        "feature/settings/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/exchangeRates/ExchangeRatesViewModel.kt",
        "feature/accounts/api/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/EnsureDefaultAccountUseCase.kt",
        "feature/accounts/impl/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/CreateAccountUseCase.kt",
        "feature/creditcards/impl/src/commonMain/kotlin/com/neoutils/finsight/database/repository/CreditCardRepository.kt",
        // The two forms that pre-select the currency of a row being created — the doors of
        // design D23, and the only sites that choose one at all.
        "feature/accounts/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/accountForm/AccountFormViewModel.kt",
        "feature/creditcards/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/creditCardForm/CreditCardFormViewModel.kt",
        // The two sites that decide what a cross-currency operation taught, or would suggest,
        // on its own date. Neither renders a figure: one writes a line of the rate history,
        // the other offers a number into a field.
        "core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/CollectOperationRateUseCase.kt",
        "core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/SuggestConvertedAmountUseCase.kt",
        "feature/dashboard/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/dashboard/DashboardPreviewFactory.kt",
    ).map { it.replace('/', java.io.File.separatorChar) }

    @Test
    fun `no consolidated figure depends on the network`() {
        val offenders = sourcesUnder("core", "feature", "app")
            .filterNot { file -> networkOwners.any { file.path.startsWith(it) } }
            .filter { file -> NETWORK_REFERENCE.containsMatchIn(file.code) }
            .map { it.path }

        assertEquals(
            emptyList(),
            offenders,
            "The locally recorded rate is the only authority in any conversion: no reading " +
                "of this app waits on a service, shows a loading state, or fails because one " +
                "is down. An external source may suggest a value inside the screen that " +
                "edits a rate — a MAY this change leaves unexercised — and nowhere else.",
        )
    }

    /**
     * The parts of the app that legitimately reach a service, **none of which produce a
     * figure**: support conversations, the analytics/crashlytics/auth services, and the
     * per-platform bootstrap that starts them. If the rate screen ever takes up the
     * suggestion the spec permits, it joins this list — and that doing so means editing this
     * test is the point.
     */
    private val networkOwners = listOf(
        "feature/support/",
        "core/analytics/",
        "core/crashlytics/",
        "core/auth/",
        "app/desktop/src/main/kotlin/com/neoutils/finsight/firebase/",
        "app/desktop/src/main/kotlin/com/neoutils/finsight/main.kt",
    )

    private companion object {
        /** A rate or a base-currency preference, named at all. */
        val RATE_REFERENCE = Regex("ExchangeRate|IExchangeRateRepository|baseCurrency")

        /** Anything that reaches a service. */
        val NETWORK_REFERENCE = Regex(
            "HttpClient|io\\.ktor|HttpURLConnection|URLConnection|NSURLSession|Firebase|Firestore",
        )

        /** A value multiplied by a rate, in either order. */
        val RATE_APPLICATION = Regex("\\*\\s*\\w*[rR]ate\\b|\\brate\\s*\\*")

        /** The base currency read as a snapshot rather than followed as a flow. */
        val BASE_CURRENCY_SNAPSHOT = Regex("baseCurrencyRepository\\.current\\(\\)")
    }
}
