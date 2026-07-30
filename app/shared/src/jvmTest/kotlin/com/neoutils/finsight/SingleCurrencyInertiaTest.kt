package com.neoutils.finsight

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The **inertia test**: while it holds, the app cannot produce a second currency.
 *
 * The risk window of the multi-currency change is the interval between "the write
 * boundary records the account's currency" and "every read aggregates by currency".
 * The plan closes it by ordering — the reads land before any path capable of creating
 * a second currency — but ordering is discipline, and discipline is not a guarantee.
 * This is the guarantee: while it passes, the window is not merely narrow, it is
 * **unreachable**, because there is no production path that denominates anything in a
 * currency other than the single one the device resolved.
 *
 * It is inverted rather than deleted once the account and card forms open the choice:
 * the expected set becomes exactly those two, and the test goes on holding forever as
 * "exactly two sites let the user pick a currency".
 *
 * It reads the repository's own sources, which no other test here does. A currency is
 * chosen at a *construction site*, and nothing observable at runtime can enumerate
 * sites — only the text can.
 */
class SingleCurrencyInertiaTest {

    private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    /** Every production Kotlin source of the repository — no test source, no build output. */
    private val productionSources: List<File> = repoRoot.walkTopDown()
        .onEnter { it.name != "build" && it.name != ".git" }
        .filter { it.isFile && it.extension == "kt" }
        .filter { file ->
            val path = file.relativeTo(repoRoot).invariantSeparatorsPath
            "/src/" in path && Regex("/src/[a-zA-Z]*Main/") in path
        }
        .toList()

    private fun File.relativePath() = relativeTo(repoRoot).invariantSeparatorsPath

    /**
     * The one expression that *decides* a currency: the device's region, reduced to
     * something the catalog offers. Naming it here is what makes a second way of
     * deciding fail this test instead of passing quietly.
     */
    private val theOneResolver = "CurrencyCatalog.reduce(localeCurrencyCode())"

    private val expectedDeciders = setOf(
        // The account a brand-new install starts with.
        "feature/accounts/api/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/EnsureDefaultAccountUseCase.kt",
        // Every account the user creates — the door a second currency will be born
        // through, once the form offers the choice.
        "feature/accounts/impl/src/commonMain/kotlin/com/neoutils/finsight/domain/usecase/CreateAccountUseCase.kt",
        // The `LIABILITY` account of a card.
        "feature/creditcards/impl/src/commonMain/kotlin/com/neoutils/finsight/database/repository/CreditCardRepository.kt",
        // Fabricated accounts of a dashboard component preview, which has to look
        // like the app it previews.
        "feature/dashboard/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/screen/dashboard/DashboardPreviewFactory.kt",
    )

    @Test
    fun `every production site that decides a currency decides it the one way`() {
        val found = productionSources
            .filter { theOneResolver in it.readText() }
            .map { it.relativePath() }
            .toSet()

        assertEquals(
            expectedDeciders,
            found,
            diagnosis(
                new = found - expectedDeciders,
                gone = expectedDeciders - found,
                whatItMeans = "A production site decides the currency of an account. " +
                    "While the app is single-currency there is exactly one way to " +
                    "decide, and these are the only places allowed to use it.",
            ),
        )
    }

    @Test
    fun `no production site names a currency in a literal`() {
        val literal = Regex("""currency\s*=\s*"[A-Z]{3}"""")
        val found = productionSources
            .filter { literal.containsMatchIn(it.readText()) }
            .map { it.relativePath() }
            .toSet()

        assertEquals(
            emptySet(),
            found,
            diagnosis(
                new = found,
                gone = emptySet(),
                whatItMeans = "A hard-coded currency code is how the app used to be " +
                    "accidentally correct: it read `BASE_CURRENCY` and meant `BRL`. " +
                    "A currency is resolved, propagated from a row, or chosen by the " +
                    "user — never written down.",
            ),
        )
    }

    private fun diagnosis(new: Set<String>, gone: Set<String>, whatItMeans: String) = buildString {
        appendLine(whatItMeans)
        new.forEach { appendLine("  NEW: $it") }
        gone.forEach { appendLine("  GONE: $it — the expected set is out of date") }
    }
}
