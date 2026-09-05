package com.neoutils.finsight.mcp

import com.neoutils.finsight.feature.mcp.api.McpPermissionAxis
import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.PreferencesSettings
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.prefs.AbstractPreferences
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The user moves a switch, and the process launched a second later reads what they chose.**
 *
 * That sequence is the whole of design D7, and it spans two processes: the window holds the choice
 * in memory after writing it, so nothing asked of the window can tell whether the choice ever left
 * it. The stdio process an agent's client launches builds everything from nothing, and what it must
 * not find is the answer before the last one.
 *
 * Two tests, at two heights, because on this platform they say different things. The first is the
 * requirement itself, end to end, over the real store. The second is the mechanism that makes the
 * first true where the JDK does not commit on its own — `flush` after every write and `sync` before
 * every read — and it is separate precisely because a platform that commits eagerly would let the
 * first pass with neither call in place.
 *
 * **These preferences are not the developer's.** Every value goes to a node of this run's own, and
 * the node is removed afterwards. It is a node under the user root and not a directory of its own
 * because `java.util.prefs.userRoot` is honoured only by the file-backed implementation: on macOS
 * the factory is `MacOSXPreferences`, which ignores it.
 */
class ServerChoiceReachesTheNextProcessTest {

    private val nodeName = "finsight-mcp-choice-${UUID.randomUUID()}"

    private val node: Preferences = Preferences.userRoot().node(nodeName)

    @AfterTest
    fun tearDown() {
        node.removeNode()
        Preferences.userRoot().flush()
    }

    /**
     * The window is still running — it is this process, and it has not exited — which is the case
     * that matters: whatever the JDK does for a process on its way out cannot be what makes this
     * work.
     */
    @Test
    fun `a choice made while the window is open is read by the process launched next`() {
        val settings = McpServerSettings(settings = PreferencesSettings(node), store = node)

        settings.setEnabled(true)
        settings.setPort(CHOSEN_PORT)
        settings.setPermission(McpPermissionAxis.OPERATE, granted = true)

        val read = readInAnotherProcess()

        assertEquals(
            "true",
            read["enabled"],
            "The process launched next read the server as switched off, so it would refuse every " +
                "call the user had just enabled.",
        )
        assertEquals(
            CHOSEN_PORT.toString(),
            read["port"],
            "The process launched next read a port other than the one just chosen.",
        )
        assertTrue(
            McpPermissionAxis.OPERATE.name in read.getValue("permissions"),
            "The process launched next did not see the axis just granted: ${read["permissions"]}",
        )
    }

    /**
     * What the first test cannot show on every platform: that the two calls are made at all.
     *
     * On this macOS a value written through `java.util.prefs` is already visible to another
     * process before anything is flushed — measured, not assumed — so the end-to-end test passes
     * there with or without the calls. On Linux the JDK's file-backed store writes on a timer of
     * up to 30 s, and the same test would fail. This one holds the requirement up everywhere by
     * counting the calls themselves.
     */
    @Test
    fun `every read syncs the store first and every write is committed at once`() {
        val store = RecordingPreferences()
        val settings = McpServerSettings(settings = MapSettings(), store = store)

        assertEquals(
            READS_AT_CONSTRUCTION,
            store.syncs.get(),
            "The four answers read at construction — whether to run, which port, the token and " +
                "the grants — were not each preceded by a sync, so a process starting now could " +
                "read a stale one.",
        )
        assertEquals(
            0,
            store.flushes.get(),
            "Construction committed something to the store, and construction only reads.",
        )

        settings.setEnabled(true)
        assertEquals(1, store.flushes.get(), "Switching the server on was not committed.")

        settings.setPort(CHOSEN_PORT)
        assertEquals(2, store.flushes.get(), "Choosing a port was not committed.")

        val syncsBeforeGrant = store.syncs.get()
        settings.setPermission(McpPermissionAxis.OPERATE, granted = true)
        assertEquals(3, store.flushes.get(), "Granting an axis was not committed.")
        assertTrue(
            store.syncs.get() > syncsBeforeGrant,
            "Reading the grants back did not sync the store first.",
        )

        settings.requireToken()
        assertEquals(4, store.flushes.get(), "The token minted for a client was not committed.")
    }

    /**
     * Runs [main] of `McpChoiceProbe` on this test's classpath and reads back the three values it
     * prints.
     */
    private fun readInAnotherProcess(): Map<String, String> {
        val java = File(File(System.getProperty("java.home"), "bin"), "java").absolutePath
        val classpath = System.getProperty("java.class.path")
        assertTrue(classpath.isNotBlank(), "There is no classpath to launch a second process with.")

        val process = ProcessBuilder(java, "-cp", classpath, PROBE_CLASS, nodeName)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertTrue(
            process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            "The second process never finished reading the choice.",
        )
        assertEquals(0, process.exitValue(), "The second process failed:\n$output")

        return output.lineSequence()
            .filter { "=" in it }
            .associate { line -> line.substringBefore('=') to line.substringAfter('=') }
    }

    /**
     * A [Preferences] that stores nothing and counts the two calls this test is about.
     *
     * Everything a `java.util.prefs` node has to implement is here because the class demands it;
     * only [flush] and [sync] carry meaning. The values themselves come from the [MapSettings]
     * beside it, which is what keeps this test off the machine's own store entirely.
     */
    private class RecordingPreferences : AbstractPreferences(null, "") {

        val flushes = AtomicInteger()

        val syncs = AtomicInteger()

        override fun flush() {
            flushes.incrementAndGet()
        }

        override fun sync() {
            syncs.incrementAndGet()
        }

        override fun putSpi(key: String, value: String) = Unit
        override fun getSpi(key: String): String? = null
        override fun removeSpi(key: String) = Unit
        override fun removeNodeSpi() = Unit
        override fun keysSpi(): Array<String> = emptyArray()
        override fun childrenNamesSpi(): Array<String> = emptyArray()
        override fun childSpi(name: String): AbstractPreferences = error("The node has no children.")
        override fun syncSpi() = Unit
        override fun flushSpi() = Unit
    }

    private companion object {

        const val PROBE_CLASS = "com.neoutils.finsight.mcp.McpChoiceProbeKt"

        /** Never the app's own 8477: the value has to be one nothing else would have written. */
        const val CHOSEN_PORT = 51789

        /** Whether to run it, which port, the token, and the grants. */
        const val READS_AT_CONSTRUCTION = 4

        const val PROBE_TIMEOUT_SECONDS = 60L
    }
}
