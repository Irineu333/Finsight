package com.neoutils.finsight.mcp.surface

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Nothing a tool answers with carries a domain type — at most an identifier.**
 *
 * `presentation-mapping` states this of every presentation surface, and it says the consumer being
 * a program rather than a person tightens the rule instead of loosening it. A screen handed a
 * domain aggregate would not compile against the component that renders it; an agent handed one
 * reads it, interprets it by itself, and reports whatever it concluded. Nothing fails.
 *
 * The same goes for the *other* surface's models. Two presentation surfaces resolve different
 * things — an icon and a theme colour mean nothing to an agent, and an account's full name is
 * redundant on a screen that shows it in the header — so a payload wearing a `TransactionUi` is a
 * screen's answer, delivered to something that did not ask a screen's question.
 *
 * It reads the sources rather than the classes, because this is a fact about what is **declared**:
 * nothing that runs can observe it, and a DTO written tomorrow in this package is covered the day
 * it is written, without anybody adding it to a list.
 */
class AgentSurfaceCarriesNoDomainTest {

    private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    // ------------------------------------------------------------------------------
    // What the surface declares
    // ------------------------------------------------------------------------------

    private data class Field(val owner: String, val name: String, val type: String)

    private val surfaceDir =
        File(repoRoot, "feature/mcp/impl/src/jvmMain/kotlin/com/neoutils/finsight/mcp/surface")

    private val fields: List<Field> = surfaceDir.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .flatMap { file ->
            val text = file.readText().withoutComments()
            Regex("""\bdata\s+class\s+(\w+)\s*\(""").findAll(text).flatMap { declaration ->
                val opening = declaration.range.last
                text.substring(opening + 1, text.matching(opening))
                    .splitOutsideBrackets()
                    .mapNotNull { it.asField(owner = declaration.groupValues[1]) }
            }
        }
        .toList()

    // ------------------------------------------------------------------------------
    // What it may not declare
    // ------------------------------------------------------------------------------

    /** Every type of the ledger and of the facades — the domain this surface translates away. */
    private val domainTypes: Set<String> = typesDeclaredIn(
        "core/ledger/src/commonMain/kotlin/com/neoutils/finsight/domain",
        "core/model/src/commonMain/kotlin/com/neoutils/finsight/domain",
    )

    /** The screen's own presentation models, and the money type they are written in. */
    private val screenTypes: Set<String> = typesDeclaredIn(
        "core/ui/src/commonMain/kotlin/com/neoutils/finsight/ui/model",
        "core/common/src/commonMain/kotlin/com/neoutils/finsight/extension/DisplayAmount.kt",
        "core/common/src/commonMain/kotlin/com/neoutils/finsight/extension/ConsolidatedAmount.kt",
    )

    // ------------------------------------------------------------------------------
    // The rules
    // ------------------------------------------------------------------------------

    /**
     * Everything below rests on the scan, so a path that stopped matching would turn all of it
     * green at once. This is the floor under that.
     */
    @Test
    fun `the scan reaches the declarations it claims to read`() {
        assertTrue(
            fields.map { it.owner }.distinct().size >= 10,
            "only ${fields.map { it.owner }.distinct()} were found in $surfaceDir; " +
                "the scan is no longer reading the surface it asserts about",
        )
        assertTrue(
            setOf("Transaction", "Account", "MoneyByCurrency", "Entry").all { it in domainTypes },
            "the domain types were not recognised: ${domainTypes.size} found",
        )
        assertTrue(
            setOf("TransactionUi", "DisplayAmount", "ConsolidatedAmount").all { it in screenTypes },
            "the screen's types were not recognised: ${screenTypes.size} found",
        )
    }

    @Test
    fun `no field of the surface is of a domain type`() {
        val leaks = fields.flatMap { field ->
            field.type.simpleNames()
                .filter { it in domainTypes }
                .map { "${field.owner}.${field.name}: ${field.type} — `$it` is a domain type" }
        }

        assertEquals(
            emptyList(),
            leaks,
            "A payload carries resolved values and at most an identifier. A domain type crossing " +
                "this boundary is one an agent will interpret for itself.\n" +
                leaks.joinToString("\n") { "  $it" },
        )
    }

    @Test
    fun `no field of the surface is one of the screen's models`() {
        val leaks = fields.flatMap { field ->
            field.type.simpleNames()
                .filter { it in screenTypes }
                .map { "${field.owner}.${field.name}: ${field.type} — `$it` is the screen's" }
        }

        assertEquals(
            emptyList(),
            leaks,
            "Each surface has its own models: what one resolves for display the other does not " +
                "use. Sharing them is how an agent comes to be answered a screen's question.\n" +
                leaks.joinToString("\n") { "  $it" },
        )
    }

    // ------------------------------------------------------------------------------
    // Reading Kotlin well enough to ask these questions of it
    // ------------------------------------------------------------------------------

    /** Every type declared under the given paths, whether a file or a directory. */
    private fun typesDeclaredIn(vararg paths: String): Set<String> = paths
        .asSequence()
        .flatMap { File(repoRoot, it).walkTopDown() }
        .filter { it.isFile && it.extension == "kt" }
        .flatMap {
            Regex("""\b(?:class|interface|object)\s+(\w+)""")
                .findAll(it.readText().withoutComments())
                .map { declaration -> declaration.groupValues[1] }
        }
        .toSet()

    /** The identifiers a type expression is built out of: `List<AgentMoney>` is two. */
    private fun String.simpleNames(): List<String> =
        Regex("""\w+""").findAll(this).map { it.value }.toList()

    private fun String.asField(owner: String): Field? {
        val declaration = substringBefore('=').trim()
        if (Regex("""\b(?:val|var)\s""") !in declaration) return null
        val colon = declaration.indexOutsideBrackets(':')
        if (colon < 0) return null
        return Field(
            owner = owner,
            name = declaration.substring(0, colon).trim().substringAfterLast(' '),
            type = declaration.substring(colon + 1).trim(),
        )
    }

    /**
     * Comments replaced by the newlines they held, so what is left is code. Reading them as code
     * would find `val amount: Double` in the prose that explains one.
     */
    private fun String.withoutComments(): String {
        val code = StringBuilder(length)
        var i = 0
        while (i < length) {
            when {
                startsWith("/*", i) -> {
                    val end = indexOf("*/", i).let { if (it < 0) length else it + 2 }
                    substring(i, end).forEach { if (it == '\n') code.append('\n') }
                    i = end
                }

                startsWith("//", i) -> i = indexOf('\n', i).let { if (it < 0) length else it }
                else -> code.append(this[i++])
            }
        }
        return code.toString()
    }

    /** The index of the delimiter closing the one at [opening]. */
    private fun String.matching(opening: Int): Int {
        var depth = 0
        for (i in opening until length) {
            if (this[i] == '(') depth++
            if (this[i] == ')' && --depth == 0) return i
        }
        error("unbalanced `(` at $opening")
    }

    /** The index of [target] outside every bracket. */
    private fun String.indexOutsideBrackets(target: Char): Int {
        var depth = 0
        for (i in indices) {
            when (this[i]) {
                '(', '[', '<' -> depth++
                ')', ']', '>' -> depth--
                target -> if (depth == 0) return i
            }
        }
        return -1
    }

    private fun String.splitOutsideBrackets(): List<String> {
        val parts = mutableListOf<String>()
        var rest = this
        while (true) {
            val comma = rest.indexOutsideBrackets(',')
            if (comma < 0) break
            parts += rest.substring(0, comma)
            rest = rest.substring(comma + 1)
        }
        parts += rest
        return parts.map { it.trim() }.filter { it.isNotEmpty() }
    }
}
