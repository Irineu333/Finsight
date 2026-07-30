package com.neoutils.finsight

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The window this change has to keep unreachable.
 *
 * Between writing a leg's currency from its account and every read aggregating **by**
 * currency there is a stretch in which a second currency would produce wrong numbers. The
 * plan's answer is not to narrow that window with care: it is to make a second currency
 * *unproducible* until the account form offers the choice, which is the last task of the
 * plan. This test is what makes that a fact rather than an intention.
 *
 * It reads the production sources, because that is where the fact lives — no runtime path
 * exists yet that could be exercised instead. Two things are asserted:
 *
 * 1. no production source names a currency by literal other than the one the app uses —
 *    the value is read from its declaration, so the rule is pinned rather than the value;
 * 2. every site that constructs an account naming a currency either names the one
 *    declaration or copies the currency of the row it is converting. Choosing one is what
 *    no site may do.
 *
 * Together they close the only two doors: a hard-coded second currency, and an account
 * built with one. The user cannot supply one, because no form offers it yet.
 *
 * When the form does offer it, this test is **inverted** rather than deleted: it becomes
 * "exactly two production sites choose the currency of an account", and keeps holding
 * forever.
 */
class SingleCurrencyInertiaTest {

    /** Where the one currency the app uses is declared. */
    private val declarationFile =
        "core/ledger/src/commonMain/kotlin/com/neoutils/finsight/domain/model/Currency.kt"

    /** The names through which a currency may be referred to while there is only one. */
    private val allowedReferences = setOf("BASE_CURRENCY", "ASSUMED_SINGLE_CURRENCY")

    /**
     * The one file allowed to name other currencies by literal: the catalog of what the app
     * *offers*. Listing a currency is not producing one — nothing is denominated in a code
     * because it appears in a list, and the only door that turns an offer into an account is
     * the form, which is the last task of the plan. The exemption is a single named file
     * rather than a pattern, so a second one cannot appear without this test being edited.
     */
    private val catalogFile =
        "core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/model/CurrencyCatalog.kt"

    @Test
    fun `no production source names a currency other than the one the app uses`() {
        val theOne = theOneCurrency()

        val offenders = productionSources()
            .filterNot { it.path == catalogFile }
            .flatMap { file ->
                CURRENCY_LITERAL.findAll(file.text)
                    .filter { it.value != theOne }
                    .map { "${file.path}: ${it.value}" }
            }

        assertEquals(
            emptyList(),
            offenders,
            "A second currency named by literal is one of the two ways it could appear " +
                "before the account form offers the choice.",
        )
    }

    /** The literal in the one declaration, so this test pins the rule and not the value. */
    private fun theOneCurrency(): String {
        val declaration = repositoryRoot().resolve(declarationFile).readText()
        return requireNotNull(CURRENCY_LITERAL.find(declaration)?.value) {
            "Expected a currency literal in $declarationFile."
        }
    }

    @Test
    fun `every account construction names the one currency the app uses`() {
        val named = productionSources().flatMap { file ->
            accountConstructions(file.text).mapNotNull { arguments ->
                val reference = CURRENCY_ARGUMENT.find(arguments)?.groupValues?.get(1)
                reference?.let { file.path to it }
            }
        }

        // Not an empty list: the writer's system accounts and the card's LIABILITY row do
        // name it, and must. What matters is *which* name they use.
        assertTrue(named.isNotEmpty(), "Expected the known account-construction sites to be found.")

        // A mapper that copies the currency of a row it is converting does not *choose*
        // one — it preserves what is already there, which is the opposite of the failure
        // under test.
        val offenders = named.filterNot { (_, reference) ->
            reference in allowedReferences || reference == "currency" || reference.endsWith(".currency")
        }

        assertEquals(
            emptyList(),
            offenders,
            "An account may only be built on the single currency the app has at this point.",
        )
    }

    private class Source(val path: String, val text: String)

    private fun productionSources(): List<Source> {
        val root = repositoryRoot()
        return listOf("core", "feature", "app")
            .map { root.resolve(it) }
            .flatMap { it.walkTopDown().toList() }
            .filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(root).path }
            .filterNot { it.contains("/build/") }
            // Test sources are where a second currency is *supposed* to appear: that is
            // how the per-currency reads are proven at all.
            .filterNot { it.contains("/src/commonTest/") || it.contains("Test/kotlin") }
            .map { Source(it, root.resolve(it).readText()) }
    }

    private fun repositoryRoot(): File {
        var candidate: File? = File("").absoluteFile
        while (candidate != null && !candidate.resolve("settings.gradle.kts").isFile) {
            candidate = candidate.parentFile
        }
        return requireNotNull(candidate) { "Could not locate the repository root from ${File("").absolutePath}." }
    }

    /**
     * The argument text of every `Account(`/`AccountEntity(` construction, by balancing
     * parentheses — a regex cannot, and truncating at the first `)` would miss exactly the
     * calls that nest one.
     */
    private fun accountConstructions(text: String): List<String> =
        ACCOUNT_CONSTRUCTION.findAll(text).mapNotNull { match ->
            val open = match.range.last
            var depth = 0
            for (index in open until text.length) {
                when (text[index]) {
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) return@mapNotNull text.substring(open + 1, index)
                    }
                }
            }
            null
        }.toList()

    private companion object {
        /** An ISO 4217 code as a literal: three upper-case letters, quoted. */
        val CURRENCY_LITERAL = Regex("\"[A-Z]{3}\"")

        /** `Account(` or `AccountEntity(`, not preceded by another identifier character. */
        val ACCOUNT_CONSTRUCTION = Regex("(?<![A-Za-z0-9_.])Account(?:Entity)?\\(")

        val CURRENCY_ARGUMENT = Regex("currency\\s*=\\s*([A-Za-z0-9_.\"]+)")
    }
}
