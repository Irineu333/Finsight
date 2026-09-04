package com.neoutils.finsight.mcp

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **The headless mode pays for the graph it uses and for nothing else.**
 *
 * `--mcp` starts from the same Koin aggregate the window does, rather than from a second one kept
 * in step by hand (design D5). The whole cost of that decision is what the aggregate might drag in:
 * a session an agent's client launches per conversation would be paying a window's start-up — the
 * Compose runtime, the toolkit under it, Firebase's initialisation — for a process with no screen
 * and nobody to sign in.
 *
 * Nothing about the code says that cannot happen: Compose and Firebase are on this classpath, one
 * binding declared eagerly would be enough, and the failure would show up as a slow, heavy process
 * on a user's machine rather than as a broken test. So the claim is made where it can be answered —
 * a JVM launched with `-verbose:class`, which says what it loaded — and the answer is read back
 * from what it printed.
 *
 * The process is given a home of its own and a preference store of its own: it opens the app's
 * database and reads the app's preferences, and doing either to the developer's would make this
 * test a thing that changes the machine it runs on.
 */
class HeadlessGraphCarriesNoUiTest {

    private val home: File = Files.createTempDirectory("finsight-headless").toFile()

    @AfterTest
    fun tearDown() {
        home.deleteRecursively()
    }

    @Test
    fun `the graph the headless mode resolves loads no compose class and no firebase`() {
        val classpath = System.getProperty("java.class.path")
        assertTrue(
            UI_PACKAGES.any { it in classpath },
            "Compose is not on this test's classpath, so a process launched from it could not " +
                "have loaded Compose either and this test would prove nothing.",
        )

        val loaded = runProbe(classpath)

        assertTrue(
            "resolved=" in loaded,
            "The probe never resolved the controller, so nothing was measured:\n${loaded.tail()}",
        )

        LOADED_BY_NOBODY.forEach { prefix ->
            val offenders = loaded.lineSequence()
                .filter { prefix in it }
                .map { it.substringAfter("] ").substringBefore(" source:") }
                .distinct()
                .take(OFFENDERS_SHOWN)
                .toList()

            assertTrue(
                offenders.isEmpty(),
                "Bringing the headless graph up loaded ${offenders.size} or more classes under " +
                    "`$prefix`, which a process with no window has no use for: $offenders",
            )
        }
    }

    /**
     * Runs [main] of `HeadlessGraphProbe` in a JVM of its own and answers everything it printed —
     * the class-load log and the probe's own line, in one stream because their order does not
     * matter and interleaving two pipes would risk one filling while the other is read.
     */
    private fun runProbe(classpath: String): String {
        val javaBin = File(File(System.getProperty("java.home"), "bin"), "java").absolutePath

        val process = ProcessBuilder(
            javaBin,
            "-verbose:class",
            "-Duser.home=${home.absolutePath}",
            "-Djava.util.prefs.PreferencesFactory=$PREFERENCES_FACTORY",
            "-cp",
            classpath,
            PROBE_CLASS,
        ).redirectErrorStream(true).start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertTrue(
            process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            "The probe never finished bringing the graph up.",
        )
        assertEquals(0, process.exitValue(), "The probe failed:\n${output.tail()}")

        return output
    }

    /** The last lines of a very long stream, which is where a failure says what it was. */
    private fun String.tail(): String = lineSequence().toList().takeLast(TAIL_LINES).joinToString("\n")

    private companion object {

        const val PROBE_CLASS = "com.neoutils.finsight.mcp.HeadlessGraphProbeKt"

        const val PREFERENCES_FACTORY = "com.neoutils.finsight.mcp.ScratchPreferencesFactory"

        /**
         * What must not be loaded. Compose is the window's runtime; `com.google.firebase` and
         * `dev.gitlive.firebase` are the two halves of the Firebase the desktop app carries, whose
         * only consumer is the support repository — a screen, and no tool.
         */
        val LOADED_BY_NOBODY = listOf("androidx.compose.", "com.google.firebase.", "dev.gitlive.")

        /** How the same libraries are named on a classpath, which is where they have to be. */
        val UI_PACKAGES = listOf("org.jetbrains.compose", "androidx.compose")

        const val OFFENDERS_SHOWN = 10

        const val TAIL_LINES = 40

        const val PROBE_TIMEOUT_SECONDS = 120L
    }
}
