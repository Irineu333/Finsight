package com.neoutils.finsight

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **A surface that draws a consolidated figure can say why it reads that way** (design
 * D21/D25).
 *
 * The badge is the only way out of the `≈` mark and the only explanation of a figure that
 * came out in parts or could not come out at all — and it is opt-in per surface, which
 * means a new widget gets it by someone remembering. Three did not: the dashboard's four
 * flow widgets drew eight consolidated figures with no badge between them, the report
 * viewer's context card drew four, and the category modal drew the month's total. Every one
 * of them was invisible to test and to review, because for a single-currency user a badge
 * that never appears looks exactly like a badge that cannot.
 *
 * So the pairing is mechanical. Reading the sources is the only way to state it: what is
 * being asserted is a property of *where an expression is written*, and nothing observable
 * at runtime enumerates surfaces.
 */
class EveryFigureCanExplainItselfTest {

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

    /**
     * The renderer itself, which receives a figure rather than showing one — it is what
     * every surface below draws *through*, so requiring it to carry a badge would be
     * requiring the paint to explain the picture.
     */
    private val renderer =
        "core/designsystem/src/commonMain/kotlin/com/neoutils/finsight/ui/component/MoneyText.kt"

    @Test
    fun `every surface that draws a figure also carries the badge`() {
        val deaf = productionSources
            .filter { it.relativePath() != renderer }
            .filter { file ->
                val text = file.readText()
                // Passing a `figure` to the single multi-term renderer is what "draws a
                // consolidated figure" *is* (design D22): every one of them goes through
                // there, so nothing that draws one can avoid saying so here.
                "MoneyText(" in text && "figure =" in text
            }
            .filterNot { "ConsolidationBadge" in it.readText() }
            .map { it.relativePath() }

        assertEquals(
            emptyList(),
            deaf,
            "A surface draws a consolidated figure with no way to explain it. For a " +
                "single-currency user this is indistinguishable from correct, and the " +
                "user it fails is the one holding two currencies and reading a total in " +
                "parts with nothing on the card to say why.",
        )
    }

    /**
     * And the badge is not merely present: the surface has to give it somewhere to lead.
     * A badge with no route to the rate archive explains a problem and then strands the
     * user in front of it, which is what D25 calls the designed path.
     */
    @Test
    fun `every badge leads somewhere`() {
        val stranded = productionSources
            .filter { "ConsolidationBadge(" in it.readText() }
            .filterNot { "onSeeRates" in it.readText() }
            .map { it.relativePath() }

        assertEquals(emptyList(), stranded, "A badge was shown without a way to the rates.")
    }
}
