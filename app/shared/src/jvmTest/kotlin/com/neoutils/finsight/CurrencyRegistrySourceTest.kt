package com.neoutils.finsight

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The set of offered currencies has exactly one source, and the ledger is not in it.**
 *
 * Both halves need a guard of the same kind as `BaseCurrencyReachTest`, and for the same
 * reason: neither violation is visible at runtime. A second list of currencies declared
 * in code would work perfectly until it diverged from the table; a ledger query naming
 * `currencies` would work perfectly until an archived currency started refusing a write
 * the ledger has no business refusing.
 */
class CurrencyRegistrySourceTest {

    private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    private fun sourcesUnder(module: String): List<File> =
        File(repoRoot, module).walkTopDown()
            .onEnter { it.name != "build" }
            .filter { it.isFile && it.extension == "kt" }
            .toList()

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
     * The seed is the **initial content of the table**, and it is the one list of
     * currencies production code may declare. Anything else declaring several codes in a
     * row is a second set, which is what this change removed.
     */
    @Test
    fun `only the seed declares a list of currencies`() {
        // Three or more ISO-shaped codes in one file, quoted — the shape of a list of
        // currencies, whatever it is called.
        val looksLikeACurrencyList = Regex(""""[A-Z]{3}"""")

        val found = productionSources
            .filter { looksLikeACurrencyList.findAll(it.readText()).count() >= 3 }
            .map { it.relativePath() }
            .toSet()

        assertEquals(
            setOf("core/model/src/commonMain/kotlin/com/neoutils/finsight/domain/model/CurrencySeed.kt"),
            found,
            "A production file declares a list of currencies. The offered set is a table " +
                "with one source; what the app ships is the initial content of its rows, " +
                "and nothing else.\n" +
                found.joinToString("\n") { "  $it" },
        )
    }

    /**
     * Every consumer reads the registry through its repository. A screen, a form or a
     * component that assembled its own set would be a second source with none of the
     * table's behaviour — no archiving, and nothing the user registered.
     */
    @Test
    fun `every consumer of the offered set reads it from the repository`() {
        val offersCurrencies = Regex("""selectableCurrencies\s*=\s*(?!emptyList)""")

        val assembledElsewhere = productionSources
            .filter { file ->
                val text = file.readText()
                offersCurrencies.containsMatchIn(text) &&
                    "ICurrencyRepository" !in text &&
                    // A UI state and a modal receive the list; they never build it.
                    "ViewModel.kt" in file.name
            }
            .map { it.relativePath() }

        assertEquals(emptyList(), assembledElsewhere)
    }

    /**
     * The ledger MUST NOT know the offered set. It persists the currency code and nothing
     * else — no foreign key, no query, no validation — which is precisely what keeps
     * offering a currency a decision of the layer above it, and what makes an archived
     * currency have one line of defence rather than two.
     */
    @Test
    fun `no ledger source names the currencies table`() {
        // The table by name — in a query, in a write, or through the types that stand
        // for it. Not the *word*: a per-currency figure legitimately calls a local
        // variable `currencies`, and the ledger is full of them.
        val namesTheTable = Regex(
            """`currencies`|(FROM|INTO|UPDATE|JOIN)\s+currencies\b""" +
                """|CurrencyEntity|CurrencyDao|ICurrencyRepository"""
        )

        val offenders = sourcesUnder("core/ledger")
            .filter { namesTheTable.containsMatchIn(it.readText()) }
            .map { it.relativePath() }

        assertTrue(
            offenders.isEmpty(),
            "The ledger names the offered set of currencies. It knows that a currency " +
                "exists and nothing whatsoever about which ones.\n" +
                offenders.joinToString("\n") { "  $it" },
        )
    }
}
