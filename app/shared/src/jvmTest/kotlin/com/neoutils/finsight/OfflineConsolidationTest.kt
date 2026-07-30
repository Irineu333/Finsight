package com.neoutils.finsight

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **No read of this app waits on the network.**
 *
 * A rate stored locally is the only authority in any conversion (design D11). An
 * external source may fill the field in as a *suggestion*, inside the screen that edits
 * a rate and nowhere else — and in v1 that suggestion is the field's placeholder and
 * nothing more. No screen shows a loading state, and none fails because a source is
 * unreachable.
 *
 * Stated as a dependency scan because that is the version of the sentence a machine can
 * check: a module with no HTTP client cannot wait on anything. The Firebase modules of
 * analytics, crashlytics, auth and support talk to the network, and they are not in the
 * path of any figure — hence the scan is scoped to the modules that are.
 */
class OfflineConsolidationTest {

    private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    /** Every module a consolidated figure passes through, from the ledger to the screen. */
    private val figurePath = listOf(
        "core/ledger",
        "core/model",
        "core/common",
        "core/database",
        "core/designsystem",
        "core/ui",
        "feature/settings/api",
        "feature/settings/impl",
    )

    @Test
    fun `no module in the path of a figure can reach the network`() {
        val networkClient = Regex("""ktor|okhttp|retrofit|java\.net\.|URLConnection""", RegexOption.IGNORE_CASE)

        val found = figurePath
            .map { File(repoRoot, "$it/build.gradle.kts") }
            .filter { it.exists() && networkClient.containsMatchIn(it.readText()) }
            .map { it.relativeTo(repoRoot).invariantSeparatorsPath }
            .toSet()

        assertEquals(
            emptySet(),
            found,
            "A module a consolidated figure passes through gained a network client. " +
                "The stored rate is the only authority; a suggestion lives in the modal " +
                "that edits a rate, and it may never become something a figure waits on.\n" +
                found.joinToString("\n") { "  NEW: $it" },
        )
    }
}
