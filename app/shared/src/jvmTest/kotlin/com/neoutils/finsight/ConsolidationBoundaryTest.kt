package com.neoutils.finsight

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where conversion is allowed to happen, and where it is not.
 *
 * The rule is one sentence: **a rate multiplies money in exactly one place, the reducer
 * in `:core:model`.** The ledger below it never converts, so "every figure is
 * `Σ entries`" stays literally true; the display type above it never combines two
 * values; and no screen, view model or UI model does the arithmetic in line, which is
 * what keeps "where did this number come from" answerable.
 *
 * Like the inertia test, it reads the repository's own sources. Conversion in line is a
 * property of *where an expression is written*, and nothing observable at runtime can
 * enumerate places — only the text can.
 */
class ConsolidationBoundaryTest {

    private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private val productionSources: List<File> = repoRoot.walkTopDown()
        .onEnter { it.name != "build" && it.name != ".git" }
        .filter { it.isFile && it.extension == "kt" }
        .filter { file ->
            val path = file.relativeTo(repoRoot).invariantSeparatorsPath
            "/src/" in path && Regex("/src/[a-zA-Z]*Main/") in path
        }
        .toList()

    private fun File.relativePath() = relativeTo(repoRoot).invariantSeparatorsPath

    @Test
    fun `only the reducer multiplies money by a rate`() {
        // `.rate` is the whole vocabulary of conversion: an `ExchangeRate` has exactly
        // one number on it, and reading that number anywhere is either the reducer
        // doing its job or a conversion happening where it must not.
        val readsARate = Regex("""\.rate\b""")
        // An allow-list, and it is meant to grow by a line at a time, deliberately: a
        // mapper that carries the number between an entity and the domain model is a
        // legitimate entry; a screen, a view model or a UI model of some *other*
        // feature never is.
        //
        // The settings feature is the documented exception, and it is one because it
        // is the feature that *is about* rates: it lists them, edits them and removes
        // them. Reading the number in order to show it is not converting money by it —
        // no amount of money passes through these files at all. Every other surface of
        // the app asks the reducer.
        val allowed = setOf(
            "core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/ConsolidateMoneyUseCase.kt",
            // The other end of a crossing, offered to a form before it is written. It
            // multiplies by a rate, which is why it lives here beside the reducer and
            // not in the modal that shows it: the three two-value flows state amounts
            // and never convert them.
            "core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/SuggestCrossCurrencyAmountUseCase.kt",
            "feature/settings/impl/src/commonMain/kotlin/com/neoutils/finsight/database/mapper/ExchangeRateMapper.kt",
            "feature/settings/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/exchangeRateForm/ExchangeRateFormModal.kt",
            "feature/settings/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/exchangeRateForm/ExchangeRateFormViewModel.kt",
            "feature/settings/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/exchangeRates/ExchangeRatesScreen.kt",
        )

        val found = productionSources
            .filter { readsARate.containsMatchIn(it.readText()) }
            .map { it.relativePath() }
            .toSet()

        assertEquals(
            allowed,
            found,
            "A rate is read outside the consolidation layer. Conversion has exactly one " +
                "owner; a screen or view model that multiplies by a rate puts a second " +
                "answer to 'how much is this worth' one line away from the first.\n" +
                (found - allowed).joinToString("\n") { "  NEW: $it" },
        )
    }

    /**
     * The edge the allow-list above would otherwise blunt.
     *
     * The settings feature is allowed to *name* a rate because it is the screen that
     * edits one. It is still not allowed to **apply** one: the moment a rate appears
     * beside a `*` or a `/` outside the reducer, "how much is this worth" has a second
     * answer.
     */
    @Test
    fun `no surface outside the reducer applies a rate to anything`() {
        val appliesARate = Regex("""[*/]\s*[\w.]*\.rate\b|\.rate\s*[*/]""")

        val found = productionSources
            .filterNot { "core/model/src/commonMain" in it.relativePath() }
            .filter { appliesARate.containsMatchIn(it.readText()) }
            .map { it.relativePath() }
            .toSet()

        assertEquals(
            emptySet(),
            found,
            "A rate is applied outside the consolidation layer.\n" +
                found.joinToString("\n") { "  NEW: $it" },
        )
    }

    @Test
    fun `the consolidation layer does not offer to sum two per-currency results`() {
        // Summing two disjoint perimeters is arithmetic on balances, and it belongs to
        // the ledger, which owns how much a figure is (`MoneyByCurrency.plus`). Offering
        // it here too would be a second implementation of a derivable rule.
        val consolidationLayer = productionSources.filter {
            "core/model/src/commonMain" in it.relativePath() &&
                ("/domain/usecase/" in it.relativePath() || "/domain/repository/" in it.relativePath())
        }

        val declaresASum = Regex("""fun\s+(plus|sum)\w*\s*\(|operator\s+fun\s+plus""")

        assertTrue(
            consolidationLayer.none { declaresASum.containsMatchIn(it.readText()) },
            "The consolidation layer answers for conversion between currencies and " +
                "nothing else. Adding two per-currency results is the ledger's, and it " +
                "has exactly one implementation there.",
        )
    }

    @Test
    fun `the ledger has no dependency that could hand it a rate or a base currency`() {
        val build = File(repoRoot, "core/ledger/build.gradle.kts").readText()

        // The guarantee is structural rather than a matter of discipline: with no
        // project dependency at all, a rate cannot reach the ledger even by accident,
        // and neither can a display preference.
        assertTrue(
            "projects." !in build,
            "`:core:ledger` gained a project dependency. It depends on no app module, " +
                "which is what makes 'the ledger never consolidates' impossible to " +
                "violate rather than merely forbidden.\n$build",
        )
    }
}
