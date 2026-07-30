package com.neoutils.finsight

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where conversion is allowed to happen, asserted over the sources — because the boundary is
 * a fact about the whole app and no single module can state it.
 *
 * Three things are pinned, and each one is a rule the compiler cannot reach:
 *
 * 1. the **ledger** knows nothing of rates or of a base currency. It is enforced by the module
 *    graph already — the consolidation layer depends on the ledger and not the other way round
 *    — but the graph would let a rate arrive as a *parameter*, and the sentence the ledger
 *    sustains is that every figure is `Σ entries`;
 * 2. **exactness is derived in one place.** Only the consolidation layer denominates a figure
 *    as approximate; anywhere else that would be a screen deciding, by hand, that its own
 *    number is an approximation — which is how the mark goes missing;
 * 3. **nothing converts in a screen, a ViewModel or a UI model.** One implementation reduces a
 *    per-currency result, and every consumer goes through it.
 */
class ConsolidationBoundaryTest {

    /** The one place a rate may be applied to a value. */
    private val consolidationLayer =
        "core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/ConsolidateFigureUseCase.kt"

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
            .filterNot { it.path == consolidationLayer }
            .filter { file -> RATE_APPLICATION.containsMatchIn(file.code) }
            .map { it.path }

        assertEquals(
            emptyList(),
            offenders,
            "Reducing a per-currency result has exactly one implementation, and every " +
                "consumer of a consolidated figure goes through it.",
        )
    }

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
    }
}
