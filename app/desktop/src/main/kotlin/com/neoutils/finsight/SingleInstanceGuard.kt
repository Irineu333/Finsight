package com.neoutils.finsight

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException

/**
 * Makes exactly one process the owner of `~/.finance/finsight.db`, always.
 *
 * Two live instances over the same file would not merely waste a connection: Room's invalidation
 * tracker does not cross processes, so a write made by one would stay invisible to the other until
 * a restart, and both would be candidates to run the same migration on the same file. Hence the
 * ownership is claimed **before** anything opens the database, and a second attempt is refused
 * rather than degraded.
 *
 * The claim is an exclusive lock on the first byte of a lock file kept beside the database. The
 * lock — not the file — is the ownership, and that is what makes it survive a process that dies
 * without cleaning up: the operating system releases the lock when the owning process ends,
 * however it ends, so the file left behind is an inert leftover that the next start locks again.
 * A stale *file* therefore never keeps the app from starting; only a live *holder* does.
 *
 * Past the first byte the owner writes its pid, so a refusal can name who is holding the database
 * instead of only stating that someone is. It sits outside the locked region on purpose: on
 * platforms where file locks are mandatory, a byte inside it would not be readable by the process
 * being refused — the one that needs to read it.
 */
internal class SingleInstanceGuard(
    private val lockFile: File = defaultLockFile(),
) {

    /** The answer to a claim, and — when refused — why, in words fit to be printed. */
    sealed interface Outcome {

        /** This process owns the database. [release] gives it back. */
        data object Acquired : Outcome

        /**
         * This process does **not** own the database and must not open it.
         *
         * [reason] covers both a live owner and a lock that could not be evaluated at all: an
         * unusable lock file is refused rather than assumed free, because assuming would be
         * assuming exactly the thing this guard exists to rule out.
         */
        data class Refused(val reason: String) : Outcome
    }

    private var handle: RandomAccessFile? = null

    private var lock: FileLock? = null

    /**
     * Claims ownership of the database, without opening it.
     *
     * Idempotent for this instance: once acquired, further calls answer [Outcome.Acquired]
     * without touching the lock file again.
     */
    fun tryAcquire(): Outcome {
        if (lock != null) return Outcome.Acquired

        val parent = lockFile.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
            return Outcome.Refused("the lock directory ${parent.absolutePath} could not be created")
        }

        val handle = try {
            RandomAccessFile(lockFile, "rw")
        } catch (cause: IOException) {
            return Outcome.Refused(unreadable(cause))
        }

        val acquired = try {
            handle.channel.tryLock(0, 1, false)
        } catch (_: OverlappingFileLockException) {
            // This very JVM already holds it. Another guard in this process is the owner, and the
            // conclusion for the caller is the same as for another process holding it.
            handle.close()
            return Outcome.Refused(alreadyRunning(owner = null))
        } catch (cause: IOException) {
            handle.close()
            return Outcome.Refused(unreadable(cause))
        }

        if (acquired == null) {
            val owner = readOwner(handle)
            handle.close()
            return Outcome.Refused(alreadyRunning(owner))
        }

        writeOwner(handle)

        this.handle = handle
        this.lock = acquired
        return Outcome.Acquired
    }

    /**
     * Gives ownership back, so another start can take it.
     *
     * Calling it is tidiness, not correctness: the lock dies with the process either way. The lock
     * file itself is left on disk, because it is the lock that is meaningful and deleting it would
     * race with a start already claiming it.
     */
    fun release() {
        runCatching { lock?.release() }
        runCatching { handle?.close() }
        lock = null
        handle = null
    }

    private fun writeOwner(handle: RandomAccessFile) {
        runCatching {
            val stamp = "\n${ProcessHandle.current().pid()}\n".toByteArray()
            handle.seek(0)
            handle.write(stamp)
            handle.setLength(stamp.size.toLong())
        }
    }

    private fun readOwner(handle: RandomAccessFile): Long? = runCatching {
        if (handle.length() <= OWNER_OFFSET) return@runCatching null
        handle.seek(OWNER_OFFSET)
        val bytes = ByteArray((handle.length() - OWNER_OFFSET).toInt().coerceAtMost(MAX_OWNER_BYTES))
        handle.readFully(bytes)
        bytes.decodeToString().trim().toLongOrNull()
    }.getOrNull()

    private fun alreadyRunning(owner: Long?): String {
        val who = owner?.let { "process $it" } ?: "another process"
        return "$who already owns the Finsight database " +
            "(it holds the lock on ${lockFile.absolutePath})"
    }

    private fun unreadable(cause: IOException): String =
        "the lock ${lockFile.absolutePath} could not be evaluated: ${cause.message ?: cause}"

    companion object {

        private const val OWNER_OFFSET = 1L

        private const val MAX_OWNER_BYTES = 32

        /** Beside the database it protects, so the two never disagree about which home they are in. */
        fun defaultLockFile(): File = File(System.getProperty("user.home"), ".finance/finsight.lock")
    }
}
