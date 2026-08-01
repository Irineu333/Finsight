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

    /**
     * Everything that produces a consolidated figure — the reducer, and whatever is built
     * on top of it, transitively.
     *
     * **The name of the reducer alone was not the rule.** A view model reaches the
     * reduction through a use case as often as directly: `ViewBudgetViewModel` shows a
     * progress bar whose spending is reduced to the limit's currency and never writes
     * `ConsolidateMoneyUseCase` anywhere, so it slipped past a detector that looked for
     * that word — the same shape of blind spot as a guard that named a callback and meant
     * "chooses a currency through the shared sheet". What makes a screen have to listen is
     * that a rate can move its number, and that travels up through whoever passes it on.
     *
     * DI modules are excluded from *contributing* names: a Koin module mentions everything
     * it wires, so letting it in would make every name reachable from every other.
     */
    private val reducingTypes: Set<String> by lazy {
        val names = mutableSetOf("ConsolidateMoneyUseCase")
        val candidates = productionSources.filterNot {
            it.name.endsWith("ViewModel.kt") || it.name.endsWith("Module.kt")
        }

        do {
            val grew = candidates.any { file ->
                val name = file.name.removeSuffix(".kt")
                if (name in names) return@any false
                if (names.none { it in file.readText() }) return@any false

                names += name
                // An interface reached through its implementation: the view model injects
                // the contract, and the file that reduces is the `...Impl`.
                name.removeSuffix("Impl").takeIf { it != name }?.let(names::add)
                true
            }
        } while (grew)

        names
    }

    @Test
    fun `every view model that reduces a figure also listens for what moves one`() {
        val listens = Regex("""ObserveConsolidationChangesUseCase""")
        val reducesAFigure = { text: String ->
            reducingTypes.any { Regex("""\b${Regex.escape(it)}\b""").containsMatchIn(text) }
        }

        val deaf = productionSources
            .filter { it.name.endsWith("ViewModel.kt") }
            .filter { reducesAFigure(it.readText()) }
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
