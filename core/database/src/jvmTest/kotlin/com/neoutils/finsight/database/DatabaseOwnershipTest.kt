package com.neoutils.finsight.database

import com.sun.management.UnixOperatingSystemMXBean
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The database gets one owner at a time, and the exclusion is the kernel's.
 *
 * **Almost everything here happens in two processes, and it has to.** A JDK file lock belongs
 * to the whole JVM: asking for one twice inside a single process raises
 * `OverlappingFileLockException` instead of being refused, so a test that stays in the
 * process would be measuring that exception and calling it exclusion. Every claim about one
 * owner refusing another is therefore made against `OwnershipProbe`, a JVM of its own on this
 * classpath.
 *
 * The one thing that does stay in the process is the other half of the same fact: holders
 * *inside* a process share the single claim, and one of them letting go must not hand the
 * database to anybody else. That is not a preference — on this platform, closing a second
 * channel on a file the process holds a lock on drops the lock, silently, while the lock
 * object still reports itself valid. The test for it is the guard against that.
 */
class DatabaseOwnershipTest {

    private val folder: File = File.createTempFile("finsight-ownership", "").let {
        it.delete()
        it.mkdirs()
        it
    }

    private val databasePath: String = File(folder, "finsight.db").absolutePath

    private val ownership = DatabaseOwnership(databasePath)

    private val probes = mutableListOf<Probe>()

    @AfterTest
    fun tearDown() {
        probes.forEach { it.close() }
        folder.deleteRecursively()
    }

    private fun probe(command: String) = Probe(command, databasePath).also { probes += it }

    @Test
    fun `the ownership another process holds is refused`() {
        val held = assertNotNull(ownership.tryAcquire(), "the ownership was free to begin with")
        try {
            assertEquals(REFUSED, probe("try").next(), "another process took a held ownership")
        } finally {
            held.release()
        }
    }

    @Test
    fun `the ownership this process asks for while another holds it is refused`() {
        val other = probe("hold")
        assertEquals(ACQUIRED, other.next(), "the other process took the ownership")

        assertNull(ownership.tryAcquire(), "the ownership was taken while another process held it")
    }

    @Test
    fun `the ownership is granted once the process holding it lets go`() {
        val other = probe("hold")
        assertEquals(ACQUIRED, other.next(), "the other process took the ownership")
        assertNull(ownership.tryAcquire(), "the ownership was free while another process held it")

        other.letGo()
        assertEquals(RELEASED, other.next(), "the other process let the ownership go")

        val held = assertNotNull(
            ownership.tryAcquire(),
            "the ownership was still refused after the process holding it let it go",
        )
        held.release()
    }

    @Test
    fun `releasing hands the ownership to another process`() {
        assertNotNull(ownership.tryAcquire()).release()

        assertEquals(ACQUIRED, probe("try").next(), "a released ownership was not free")
    }

    @Test
    fun `waiting ends the moment the other process lets go`() {
        val other = probe("hold")
        assertEquals(ACQUIRED, other.next(), "the other process took the ownership")
        assertNull(ownership.tryAcquire(), "the ownership was free while another process held it")

        // Marked before the thread is started, so that the wait measured here cannot be
        // shorter than the hold it was waiting on.
        val start = TimeSource.Monotonic.markNow()
        val releasing = Thread {
            Thread.sleep(HANDOVER.inWholeMilliseconds)
            other.letGo()
        }.apply { start() }

        val held = ownership.acquire(DatabaseOwnership.WAIT_LIMIT)
        val waited = start.elapsedNow()
        releasing.join()

        assertNotNull(held, "waiting expired although the other process let go within it").release()
        assertTrue(waited >= HANDOVER, "the ownership was granted before the other process let go")
        assertTrue(waited < DatabaseOwnership.WAIT_LIMIT, "waiting ran to its limit anyway: $waited")
    }

    @Test
    fun `waiting gives up at the limit it was given`() {
        val other = probe("hold")
        assertEquals(ACQUIRED, other.next(), "the other process took the ownership")

        val (held, waited) = timed { ownership.acquire(LIMIT) }

        assertNull(held, "the ownership was taken while another process held it")
        assertTrue(waited >= LIMIT, "waiting gave up before its limit: $waited")
        assertTrue(waited < LIMIT * SLACK, "waiting outlasted its limit: $waited")
    }

    @Test
    fun `holders inside one process share the claim the first of them took`() {
        val first = assertNotNull(ownership.tryAcquire(), "the ownership was free to begin with")
        val second = assertNotNull(
            DatabaseOwnership(databasePath).tryAcquire(),
            "a second holder in this process was refused the ownership it already has",
        )

        assertEquals(REFUSED, probe("try").next(), "another process took a held ownership")

        second.release()
        assertEquals(
            REFUSED,
            probe("try").next(),
            "one holder letting go handed the database away while another still held it",
        )

        first.release()
        assertEquals(ACQUIRED, probe("try").next(), "the last holder letting go did not free it")
    }

    @Test
    fun `taking and releasing the ownership does not spend a descriptor a time`() {
        val operatingSystem = ManagementFactory.getOperatingSystemMXBean()
        if (operatingSystem !is UnixOperatingSystemMXBean) return

        assertNotNull(ownership.tryAcquire(), "the ownership was free to begin with").release()
        val before = operatingSystem.openFileDescriptorCount

        repeat(ROUNDS) {
            assertNotNull(ownership.tryAcquire(), "the ownership was refused on round $it").release()
        }

        val spent = operatingSystem.openFileDescriptorCount - before
        assertTrue(spent <= SLACK, "$ROUNDS rounds left $spent descriptors open")
    }

    @Test
    fun `the ownership is taken beside the database and never on it`() {
        val held = assertNotNull(ownership.tryAcquire(), "the ownership was free to begin with")
        try {
            assertEquals(
                listOf("finsight.db.ownership"),
                folder.listFiles().orEmpty().map { it.name },
                "the ownership is taken on a file of its own, beside the database",
            )
            assertFalse(File(databasePath).exists(), "taking the ownership opened the database")
        } finally {
            held.release()
        }
    }

    @Test
    fun `the window waits ten seconds and no longer`() {
        assertEquals(
            10.seconds,
            DatabaseOwnership.WAIT_LIMIT,
            "the limit the window waits for the ownership is declared as ten seconds (design D10)",
        )
    }

    private fun <T> timed(block: () -> T): Pair<T, Duration> {
        val start = TimeSource.Monotonic.markNow()
        return block() to start.elapsedNow()
    }
}

/**
 * A JVM of its own, running [OwnershipProbe] on this classpath, launched to be the *other*
 * process in every claim about exclusion.
 *
 * `stdout` carries the single words the probe answers and nothing else; `stderr` is inherited
 * so that a probe which dies says so in the suite's own output instead of in a pipe nobody
 * reads.
 */
private class Probe(command: String, databasePath: String) : AutoCloseable {

    private val process = ProcessBuilder(
        File(File(System.getProperty("java.home"), "bin"), "java").absolutePath,
        "-cp",
        System.getProperty("java.class.path"),
        "com.neoutils.finsight.database.OwnershipProbeKt",
        command,
        databasePath,
    ).redirectError(ProcessBuilder.Redirect.INHERIT).start()

    private val answers = process.inputStream.bufferedReader()
    private val orders = process.outputStream.bufferedWriter()

    fun next(): String = answers.readLine() ?: "the probe answered nothing and exited with " +
        process.waitFor()

    fun letGo() {
        orders.write("release")
        orders.newLine()
        orders.flush()
    }

    override fun close() {
        process.destroyForcibly()
        process.waitFor()
    }
}

/** How long the other process is left holding the ownership before it is told to let go. */
private val HANDOVER = 300.milliseconds

/** A waiting limit short enough to expire inside a test and long enough to be measurable. */
private val LIMIT = 500.milliseconds

/**
 * What a machine under load is allowed to add — to a waiting limit, and to the descriptors a
 * round of taking and releasing leaves behind. A channel left open by every release would
 * account for [ROUNDS] of them, not a handful.
 */
private const val SLACK = 8

private const val ROUNDS = 200
