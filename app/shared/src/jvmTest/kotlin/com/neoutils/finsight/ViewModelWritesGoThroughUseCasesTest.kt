package com.neoutils.finsight

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **A view model does not write to a repository.**
 *
 * Every write is a rule — what may be blank, what must be unique, what is trimmed, what
 * the creation instant is, which of three things a filled form turns out to be — and a
 * rule executed inside a view model has exactly one caller: that screen. The app is
 * growing a second caller that has no screen, and the moment it exists, a rule living in
 * a view model has to be written a second time. Two copies of one rule are one edit away
 * from disagreeing, and the disagreement shows up as the screen and the agent doing
 * different things to the same account.
 *
 * So the operation moves down to the use case that owns it, and the view model calls it —
 * which is also the only place a refusal has somewhere to be reported from.
 *
 * The direct writes that remain are listed below by file, with the calls they make and
 * the reason each is still there. The comparison is exact in both directions: a new
 * direct write fails because it is not listed, a listed file that gains a second one
 * fails too, and a file that stops writing directly fails until its entry is dropped.
 * Nothing here is a category exemption — every line is a name somebody has to defend.
 */
class ViewModelWritesGoThroughUseCasesTest {

    private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    /** Every view model the app ships, whatever module and target it belongs to. */
    private val viewModels: List<File> = repoRoot.walkTopDown()
        .onEnter { it.name != "build" && it.name != ".git" }
        .filter { it.isFile && it.name.endsWith("ViewModel.kt") }
        .filter { Regex("""/src/[a-zA-Z]*Main/""") in it.relativeTo(repoRoot).invariantSeparatorsPath }
        .toList()

    /**
     * A write is one of these verbs applied to a repository or a DAO. The receiver is
     * what makes it precise: `updateState` on a flow and `createPreview` on a factory are
     * not writes, and no verb alone tells them apart from `updateTransaction` on a
     * repository.
     *
     * The verbs are the ones the repository and DAO interfaces actually declare — not a
     * guess at what a write might be called. Nothing they *read* begins with any of them,
     * which is what keeps the list from having to name the two hundred reads to exclude.
     */
    private val write = Regex(
        """\b(\w*(?:[Rr]epository|[Dd]ao))\s*\.\s*""" +
            """((?:insert|upsert|update|delete|save|remove|create|add|set|put|write|clear""" +
            """|archive|unarchive|close|reopen|record|emit|detach|dismiss|confirm)\w*)\s*\(""",
    )

    private val direct: Map<String, List<String>> = viewModels
        .associate { file ->
            file.relativeTo(repoRoot).invariantSeparatorsPath to
                write.findAll(file.readText())
                    .map { "${it.groupValues[1]}.${it.groupValues[2]}" }
                    .distinct()
                    .sorted()
                    .toList()
        }
        .filterValues { it.isNotEmpty() }

    @Test
    fun `the scan reaches the view models it claims to read`() {
        assertTrue(
            viewModels.size >= 50,
            "only ${viewModels.size} view models were found; the scan is no longer " +
                "reading the surface it asserts about.",
        )
    }

    @Test
    fun `no view model writes to a repository the domain owns`() {
        assertEquals(
            REMAINING,
            direct,
            "A write from a view model is a rule with one caller, and the app now has a " +
                "second caller with no screen.\n" +
                (direct.keys - REMAINING.keys).joinToString("\n") {
                    "  NEW: $it — ${direct[it]}; the operation belongs to a use case"
                } +
                (REMAINING.keys - direct.keys).joinToString("\n") {
                    "  LISTED BUT CLEAN: $it — it writes through a use case now; drop the entry"
                } +
                (REMAINING.keys intersect direct.keys)
                    .filter { REMAINING[it] != direct[it] }
                    .joinToString("\n") {
                        "  CHANGED: $it — listed ${REMAINING[it]}, found ${direct[it]}"
                    },
        )
    }

    private companion object {

        /**
         * What still writes straight to a repository, and why each one is not one of the
         * operations this change moved into the domain.
         */
        val REMAINING = mapOf(
            // Layout preferences: which cards the dashboard shows and in what order, and
            // whether the hint about editing that layout has been seen. Neither is a
            // domain rule — they move no money, have no invariant to refuse, and no
            // surface other than this screen can state them.
            "feature/dashboard/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/" +
                "screen/dashboard/DashboardViewModel.kt" to
                listOf(
                    "dashboardPreferencesRepository.dismissEditTip",
                    "dashboardPreferencesRepository.save",
                ),

            // The base currency, which is a display preference and not a fact about any
            // account: it decides what totals are read in, and changing it rewrites
            // nothing (`currency-consolidation`).
            "feature/settings/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/" +
                "screen/settings/SettingsViewModel.kt" to
                listOf("baseCurrencyRepository.set"),

            // The exchange-rate archive. The agent surface must never write a rate or
            // change the base currency — the two exclusions whose reason is asymmetric,
            // silent damage — so this operation has no second caller to share a rule
            // with, by decision rather than by omission.
            "feature/settings/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/" +
                "modal/exchangeRateForm/ExchangeRateFormViewModel.kt" to
                listOf("exchangeRateRepository.save"),

            // Removing a rate from that same archive, and listed for the same reason: it
            // is the other half of the form above, asked on its own sheet because a
            // deletion is confirmed before it happens.
            "feature/settings/impl/src/commonMain/kotlin/com/neoutils/finsight/ui/" +
                "modal/deleteExchangeRate/DeleteExchangeRateViewModel.kt" to
                listOf("exchangeRateRepository.remove"),
        )
    }
}
