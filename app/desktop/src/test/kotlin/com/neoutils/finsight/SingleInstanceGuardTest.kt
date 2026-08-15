package com.neoutils.finsight

import java.io.File
import java.io.RandomAccessFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SingleInstanceGuardTest {

    private val directory: File = File.createTempFile("finsight-guard", "").let { file ->
        file.delete()
        file.mkdirs()
        file
    }

    private val lockFile = File(directory, "finsight.lock")

    private val guards = mutableListOf<SingleInstanceGuard>()

    @AfterTest
    fun tearDown() {
        guards.forEach { it.release() }
        directory.deleteRecursively()
    }

    private fun guard(file: File = lockFile) = SingleInstanceGuard(file).also { guards += it }

    @Test
    fun `the first claim owns the database`() {
        assertEquals(SingleInstanceGuard.Outcome.Acquired, guard().tryAcquire())
    }

    @Test
    fun `a second claim while the first holds is refused and names the owner`() {
        guard().tryAcquire()

        val refused = assertIs<SingleInstanceGuard.Outcome.Refused>(guard().tryAcquire())

        assertContains(refused.reason, "already owns the Finsight database")
        assertContains(refused.reason, lockFile.absolutePath)
    }

    @Test
    fun `claiming twice from the same guard stays acquired`() {
        val guard = guard()

        assertEquals(SingleInstanceGuard.Outcome.Acquired, guard.tryAcquire())
        assertEquals(SingleInstanceGuard.Outcome.Acquired, guard.tryAcquire())
    }

    @Test
    fun `a lock file left behind by a dead process does not keep the app from starting`() {
        // What a process that died without releasing leaves on disk: the file, with its pid still
        // written in it, and no live holder. The lock is what ownership is, so this must not block.
        RandomAccessFile(lockFile, "rw").use { it.write("\n424242\n".toByteArray()) }

        assertTrue(lockFile.exists())
        assertEquals(SingleInstanceGuard.Outcome.Acquired, guard().tryAcquire())
    }

    @Test
    fun `releasing hands ownership to the next start`() {
        val first = guard()
        first.tryAcquire()
        first.release()

        assertEquals(SingleInstanceGuard.Outcome.Acquired, guard().tryAcquire())
    }

    @Test
    fun `the owner writes its own pid`() {
        guard().tryAcquire()

        val written = lockFile.readText().trim().toLongOrNull()

        assertEquals(ProcessHandle.current().pid(), written)
    }

    @Test
    fun `a lock that cannot be evaluated is refused, never assumed free`() {
        // The lock's directory is occupied by a regular file: nothing can be locked there, and the
        // guard must not read that as "no one owns the database".
        val occupied = File(directory, "occupied")
        occupied.writeText("not a directory")

        val refused = assertIs<SingleInstanceGuard.Outcome.Refused>(
            guard(File(occupied, "finsight.lock")).tryAcquire(),
        )

        assertContains(refused.reason, "could not be")
    }

    @Test
    fun `a live holder in another process refuses this one`() {
        val holder = LockHolder.start(lockFile)
        try {
            val refused = assertIs<SingleInstanceGuard.Outcome.Refused>(guard().tryAcquire())

            assertContains(refused.reason, "process ${holder.pid()}")
        } finally {
            holder.destroy()
            holder.waitFor()
        }
    }
}

/**
 * A real second process holding the lock, which is the only way to exercise what the guard is
 * actually for: a lock is per-process, and two claims inside this JVM would prove something else.
 */
private object LockHolder {

    fun start(lockFile: File): Process {
        val java = File(File(System.getProperty("java.home"), "bin"), "java").absolutePath
        val process = ProcessBuilder(
            java,
            "-cp",
            System.getProperty("java.class.path"),
            "com.neoutils.finsight.LockHolderMainKt",
            lockFile.absolutePath,
        ).redirectErrorStream(true).start()

        // The holder prints one line once the lock is its own; reading it is how this test knows
        // the race is over without sleeping on a guess.
        val ready = process.inputStream.bufferedReader().readLine()
        check(ready == "locked") { "the lock holder did not start: $ready" }

        return process
    }
}
