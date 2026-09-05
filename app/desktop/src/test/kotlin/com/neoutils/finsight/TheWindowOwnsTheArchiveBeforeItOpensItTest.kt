package com.neoutils.finsight

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The window claims the archive **before** it builds the graph that opens it, and holds the claim
 * for as long as it exists.
 *
 * Not a claim about the lock — `DatabaseOwnershipTest` owns that — but about **where in the
 * start-up it is taken**, which is the whole of what the ownership buys. A headless process asks
 * for the archive a call at a time and executes only if it gets it; take the claim after the graph
 * and there is an interval in which the window is already reading the database and answering `no`
 * to nothing, so a call arriving in it writes underneath a window that will never hear about it.
 * The interval is invisible: both processes work, and one screen quietly shows figures from before
 * a write.
 *
 * It is asserted on the source because a `main` that opens a window cannot be run in a suite, and
 * the ordering of two lines is not something the compiler has an opinion about. The file is found
 * by what it names rather than by its path, so extracting the window's body somewhere else moves
 * the assertion with it — what would fail here is the ordering going away, or a second place taking
 * the ownership.
 */
class TheWindowOwnsTheArchiveBeforeItOpensItTest {

    private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").exists() }

    /**
     * The production sources of the desktop entry point, minus their imports.
     *
     * Without that subtraction the ordering would be read off the import list, where every name
     * appears once in alphabetical order and none of them is being called.
     */
    private val entryPoints: Map<String, String> = File(repoRoot, "app/desktop/src/main")
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .associate { file ->
            file.relativeTo(repoRoot).invariantSeparatorsPath to file.readLines()
                .filterNot { it.startsWith("import ") }
                .joinToString(separator = "\n")
        }
        .filterValues { OWNERSHIP in it }

    /**
     * One place takes it, so there is one ordering to reason about. A second holder inside the
     * window would be a claim nobody released at a moment nobody chose.
     */
    @Test
    fun `the window takes the ownership in exactly one place`() {
        assertEquals(
            1,
            entryPoints.size,
            "The desktop entry point takes the ownership of the archive in ${entryPoints.size} " +
                "places, and it is a claim with one lifetime: the window's.\n" +
                entryPoints.keys.joinToString("\n") { "  $it" },
        )
    }

    @Test
    fun `the ownership is taken before the graph that opens the database`() {
        val (path, source) = entryPoints.entries.single().toPair()

        val claimed = source.indexOf(OWNERSHIP)
        val graph = source.indexOf(GRAPH)

        assertTrue(graph >= 0, "$path no longer builds the object graph, so this is out of date.")
        assertTrue(
            claimed < graph,
            "$path builds the graph before claiming the archive. The graph is what opens the " +
                "database, so between the two the window is reading an archive a headless " +
                "process is still free to write — and nothing in the app would report it.",
        )
    }

    /**
     * And it lasts as long as the window: given back where the window is torn down, not somewhere
     * in the middle of it.
     */
    @Test
    fun `the ownership is given back where the window ends`() {
        val (path, source) = entryPoints.entries.single().toPair()

        val released = source.indexOf(RELEASE)
        val exit = source.indexOf(EXIT)

        assertTrue(
            released >= 0,
            "$path never releases the ownership. The process exiting would drop it, which makes " +
                "the lifetime a side effect instead of a decision.",
        )
        assertTrue(
            exit in (released + 1)..source.length,
            "$path releases the ownership somewhere other than on the way out. It is held for " +
                "exactly as long as the window is, and a release before the end is an archive " +
                "another process may take while this one still has it open.",
        )
    }

    private companion object {

        /** What taking the claim is spelled as, wherever the entry point does it. */
        const val OWNERSHIP = "DatabaseOwnership("

        /** What opens the database: the graph the database binding lives in. */
        const val GRAPH = "startKoin"

        const val RELEASE = "ownership?.release()"

        const val EXIT = "exitApplication()"
    }
}
