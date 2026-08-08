package com.neoutils.finsight

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **No surface drops a term because of layout** (`money-display`).
 *
 * A figure is a list of terms, and a surface either shows all of them or degrades to
 * one *and says so* (design D20). What must not exist is the third case: a surface that
 * takes a term because that is what fits, producing a number indistinguishable from a
 * complete one. That failure is silent, which is exactly what the approximation mark
 * exists to prevent.
 *
 * So the degradation has a single owner — `ConsolidatedAmount.degradedTerm()` — and
 * this test says that nobody else picks a term out of the list. Reading the sources is
 * the only way to state it: choosing a term is a property of *where an expression is
 * written*, and nothing observable at runtime can enumerate places.
 */
class NothingIsTruncatedSilentlyTest {

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

    /** The type and the two rules over it — the one place allowed to reach into `terms`. */
    private val owner =
        "core/common/src/commonMain/kotlin/com/neoutils/finsight/extension/ConsolidatedAmount.kt"

    @Test
    fun `only the declared degradation picks a term out of a figure`() {
        // Taking one term, or a prefix of them, out of a figure. It is the *figure's*
        // list that matters, hence the leading dot — a local named `terms` inside the
        // reducer that builds one is not a surface choosing what to show. `.terms.any`
        // and `.terms.map` are not here either: they read every term and drop none.
        val picksATerm = Regex("""\.terms\s*(\[|\.\s*(first|last|single|take|drop|getOrNull)\b)""")

        val found = productionSources
            .filter { it.relativePath() != owner }
            .filter { picksATerm.containsMatchIn(it.readText()) }
            .map { it.relativePath() }

        assertEquals(
            emptyList(),
            found,
            "A surface picked a term out of a figure on its own. Where the whole " +
                "figure does not fit, the surface calls `degradedTerm()` — which is " +
                "the same choice, made once, in the open, and paired with saying that " +
                "a term was left out.",
        )
    }

    @Test
    fun `the figure cannot be rendered without its mark`() {
        val type = File(repoRoot, owner).readText()

        // No default on `isApproximate`: a figure carried without its exactness is the
        // failure the type exists to make impossible, so it cannot be omitted at a
        // construction site.
        assertEquals(
            null,
            Regex("""val\s+isApproximate\s*:\s*Boolean\s*=""").find(type)?.value,
            "`isApproximate` gained a default. Exactness is not optional: a figure " +
                "that can be built without it is a figure that can lie by omission.",
        )
    }
}
