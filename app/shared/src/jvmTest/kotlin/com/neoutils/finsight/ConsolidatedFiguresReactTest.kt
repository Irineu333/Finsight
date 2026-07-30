package com.neoutils.finsight

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **A screen that shows a consolidated figure listens for what moves one.**
 *
 * The ledger's own trigger is a `SELECT COUNT(*) FROM entries`, and registering,
 * correcting or removing a rate writes no entry — neither does changing the base
 * currency. So a view model wired only to the ledger keeps whatever it last computed:
 * the user registers the dollar in settings, comes back, and the dashboard still shows
 * two terms and an `≈` for a figure that now has a rate.
 *
 * `ObserveConsolidationChangesUseCase` is the mechanism, and building it is not the same
 * as using it — it sat bound in Koin with no consumer at all, while its own KDoc claimed
 * the promise would be false without it. This test is what makes "built" and "wired" the
 * same thing: **whoever reduces a figure also listens for what changes one.**
 *
 * It reads the sources because that is the form of the sentence — it is about which
 * dependencies a class declares, and nothing observable at runtime enumerates classes.
 */
class ConsolidatedFiguresReactTest {

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
    fun `every view model that reduces a figure also listens for what moves one`() {
        val reducesAFigure = Regex("""ConsolidateMoneyUseCase""")
        val listens = Regex("""ObserveConsolidationChangesUseCase""")

        val deaf = productionSources
            .filter { it.name.endsWith("ViewModel.kt") }
            .filter { reducesAFigure.containsMatchIn(it.readText()) }
            .filterNot { listens.containsMatchIn(it.readText()) }
            .map { it.relativePath() }

        assertEquals(
            emptyList(),
            deaf,
            "A view model reduces a figure without listening for what changes one. A rate " +
                "registered in settings writes no entry, so its figure would keep the " +
                "value it had when the screen opened.",
        )
    }

    /**
     * And the mechanism is not decoration: it has consumers. A test that only asserted
     * the pairing above would still pass with nobody using it, since a view model with
     * no reducer is not required to listen.
     */
    @Test
    fun `the mechanism has consumers outside its own module`() {
        val consumers = productionSources
            .filterNot { it.relativePath().startsWith("core/model/") }
            .filter { "ObserveConsolidationChangesUseCase" in it.readText() }
            .map { it.relativePath() }

        assertEquals(
            true,
            consumers.isNotEmpty(),
            "`ObserveConsolidationChangesUseCase` is bound and used by nobody — the " +
                "reactivity it exists to provide does not happen.",
        )
    }
}
