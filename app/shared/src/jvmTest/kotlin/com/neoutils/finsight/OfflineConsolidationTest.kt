package com.neoutils.finsight

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **No read of this app waits on the network.**
 *
 * A rate stored locally is the only authority in any conversion (design D11). No screen
 * shows a loading state, and none fails because a source is unreachable.
 *
 * Stated as a dependency scan because that is the version of the sentence a machine can
 * check: a module with no HTTP client cannot wait on anything. The Firebase modules of
 * analytics, crashlytics, auth and support talk to the network, and they are not in the
 * path of any figure — hence the scan is scoped to the modules that are.
 *
 * **`feature/settings/impl` is deliberately out of the scan, and the guarantee did not
 * weaken.** It holds the one HTTP client of the app, because a remote source now *writes*
 * the archive as its third writer, beside the harvest and the user. What keeps the
 * sentence true is no longer the absence of a client in this module — it is the
 * **direction of the flow**: the network writes rows, and every figure reads the same
 * local table it always read. That half is not checkable by a dependency scan, so it has
 * a gate of its own, by name, in `RemoteSourceIsNeverReadTest` — which also owns the
 * reciprocal claim this scan used to make, that no module outside this one declares Ktor.
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
