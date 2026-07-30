package com.neoutils.finsight

import java.io.File
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

    private class Source(val path: String, val text: String) {
        /**
         * The file with its comments removed. Without this, prose *about* multiplying by a
         * rate reads as multiplying by a rate — a KDoc line starts with an asterisk, and the
         * pattern that finds the operator finds that too.
         */
        val code: String get() = text
            .replace(BLOCK_COMMENT, "")
            .replace(LINE_COMMENT, "")
    }

    private fun sourcesUnder(vararg roots: String): List<Source> {
        val repository = repositoryRoot()
        return roots
            .map { repository.resolve(it) }
            .flatMap { it.walkTopDown().toList() }
            .filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(repository).path }
            .filterNot { it.contains("/build/") }
            // Test sources are where the boundary is exercised from both sides.
            .filterNot { it.contains("Test/kotlin") }
            .map { Source(it, repository.resolve(it).readText()) }
    }

    private fun repositoryRoot(): File {
        var candidate: File? = File("").absoluteFile
        while (candidate != null && !candidate.resolve("settings.gradle.kts").isFile) {
            candidate = candidate.parentFile
        }
        return requireNotNull(candidate) { "Could not locate the repository root." }
    }

    private companion object {
        val BLOCK_COMMENT = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("//[^\n]*")

        /** A rate or a base-currency preference, named at all. */
        val RATE_REFERENCE = Regex("ExchangeRate|IExchangeRateRepository|baseCurrency")

        /** A value multiplied by a rate, in either order. */
        val RATE_APPLICATION = Regex("\\*\\s*\\w*[rR]ate\\b|\\brate\\s*\\*")
    }
}
