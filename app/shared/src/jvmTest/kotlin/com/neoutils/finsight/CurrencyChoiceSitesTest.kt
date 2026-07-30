package com.neoutils.finsight

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where a currency is **chosen**, and where it may only be carried.
 *
 * This is the inversion of the inertia test that guarded the plan: while no form offered the
 * choice, the rule was "no production site chooses a currency, so a second one is unproducible".
 * The account and card forms are now the door, and the rule becomes the one that keeps holding
 * forever — *exactly two* production sites choose the currency of a row of the chart of
 * accounts, and any third fails this test.
 *
 * Two things are asserted, and they are the two halves of the same sentence:
 *
 * 1. the currency **control** of an account or a card exists in exactly two files. A third
 *    form growing one is a third door, and doors are what this test counts;
 * 2. every site that constructs an account naming a currency either receives it as an argument
 *    or copies the row it is converting. **Choosing** one remains something no other site does
 *    — a hard-coded currency reaching an account is exactly as wrong now as it was before the
 *    door opened.
 *
 * The budget form also opens a currency picker, and deliberately does not count: it chooses
 * among the currencies the user's accounts already have, denominating nothing new (design D13).
 * That is why the control, and not the picker, is what is counted.
 */
class CurrencyChoiceSitesTest {

    /** The component that *is* the currency line of an account or card form (design D23). */
    private val currencyControl = Regex("CurrencySelectorRow\\(")

    /** Where that component is declared — the definition is not a use of it. */
    private val controlDeclaration =
        "core/designsystem/src/commonMain/kotlin/com/neoutils/finsight/ui/component/CurrencySelectorRow.kt"

    /**
     * The two doors, and the only two. `AccountFormModal` is where an account's currency is
     * decided; `CreditCardFormModal` is where the `LIABILITY` row a card projects onto gets
     * its own, by the same rule and with the same control.
     */
    private val currencyChoosers = listOf(
        "feature/accounts/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/accountForm/AccountFormModal.kt",
        "feature/creditcards/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/modal/creditCardForm/CreditCardFormModal.kt",
    ).map { it.replace('/', File.separatorChar) }

    @Test
    fun `exactly two production sites choose the currency of an account`() {
        val choosers = productionSources()
            .filterNot { it.path == controlDeclaration.replace('/', File.separatorChar) }
            .filter { currencyControl.containsMatchIn(it.text) }
            .map { it.path }
            .sorted()

        assertEquals(
            currencyChoosers.sorted(),
            choosers,
            "A currency is chosen in the account form and in the card form, and nowhere " +
                "else. A third site would be a third answer to what denominates a row of " +
                "the chart of accounts.",
        )
    }

    @Test
    fun `no account is built on a currency its site chose by itself`() {
        val named = productionSources().flatMap { file ->
            accountConstructions(file.text).mapNotNull { arguments ->
                val reference = CURRENCY_ARGUMENT.find(arguments)?.groupValues?.get(1)
                reference?.let { file.path to it }
            }
        }

        // Not an empty list: the writer's system accounts, the card's LIABILITY row and the
        // account the user creates all name it, and must. What matters is *which* name.
        assertTrue(named.isNotEmpty(), "Expected the known account-construction sites to be found.")

        // A currency that arrives as an argument, or one copied off the row being converted,
        // is carried and not chosen — which is the opposite of the failure under test.
        val offenders = named.filterNot { (_, reference) ->
            reference in allowedReferences || reference == "currency" || reference.endsWith(".currency")
        }

        assertEquals(
            emptyList(),
            offenders,
            "An account's currency is carried from the form that chose it, never named by " +
                "the site that builds the row.",
        )
    }

    /**
     * The names through which an account's currency may reach a construction site.
     *
     * `LAST_RESORT_CURRENCY` is the consolidation layer's fallback for a locale naming a
     * currency the app does not offer; `baseCurrencyRepository.current` is a site deferring to
     * the one preference the user has, which is what pre-selects a form rather than deciding
     * for it.
     */
    private val allowedReferences = setOf(
        "LAST_RESORT_CURRENCY",
        "baseCurrencyRepository.current",
    )

    private class Source(val path: String, val text: String)

    private fun productionSources(): List<Source> {
        val root = repositoryRoot()
        return listOf("core", "feature", "app")
            .map { root.resolve(it) }
            .flatMap { it.walkTopDown().toList() }
            .filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(root).path }
            .filterNot { it.contains("${File.separatorChar}build${File.separatorChar}") }
            .filterNot { it.contains("${File.separatorChar}commonTest${File.separatorChar}") || it.contains("Test${File.separatorChar}kotlin") }
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
        /** `Account(` or `AccountEntity(`, not preceded by another identifier character. */
        val ACCOUNT_CONSTRUCTION = Regex("(?<![A-Za-z0-9_.])Account(?:Entity)?\\(")

        val CURRENCY_ARGUMENT = Regex("currency\\s*=\\s*([A-Za-z0-9_.\"]+)")
    }
}
