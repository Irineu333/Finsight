package com.neoutils.finsight.database

import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Which process answers for the database, decided by the operating system rather than by
 * agreement between the processes.
 *
 * Two processes of this app can be running at the same time — the window, and the headless
 * one a client launches — and only one of them may open the archive to write it. The claim
 * is an exclusive lock the kernel holds on a file beside the archive, so a process that asks
 * while another holds it is refused whatever either of them believes about the other, and a
 * process that dies holding it loses it without having to say so.
 *
 * Sounding out a port or reading a pid file would be an agreement, and an agreement has a
 * gap: the window is already collecting `Flow`s well before it binds anything, so there is an
 * interval in which no sounding would call it open. The claim is taken before the database is
 * opened, and has no such interval.
 *
 * **The file the lock is taken on is never the archive.** SQLite takes locks of its own on
 * the file it opens, and a second locker on the same bytes would be arguing with it.
 *
 * **The claim belongs to the process, not to the caller that took it.** A JDK file lock is
 * held by the whole JVM: a second `tryLock` on the same file from the same process raises
 * `OverlappingFileLockException` rather than answering no, and merely opening and closing a
 * further channel on that file drops the lock the process already holds — while
 * `FileLock.isValid()` goes on answering `true`. So exactly one channel per file is ever
 * opened here, holders inside the process share the single claim, and it goes back to the
 * kernel when the last of them releases. What is being excluded is another process, which is
 * what the lock is for; inside one process the database has one connection pool and needs no
 * second gate. The overlapping claim the JDK raises on cannot arise through this API, because
 * a second [DatabaseOwnership] naming the same file resolves to the same claim.
 */
class DatabaseOwnership(databasePath: String = defaultDatabasePath()) {

    private val file: File = ownershipFile(databasePath)

    /**
     * The ownership if it is free at this instant, and `null` if another process holds it —
     * one attempt, no waiting.
     *
     * `null` is also the answer when the lock cannot be taken at all: a directory that
     * refuses the file, a filesystem that does not lock. Nobody owns the database then, and
     * refusing is the safe side of that — a caller that would have written locally does not,
     * and one that only waits carries on when its limit expires.
     */
    fun tryAcquire(): Ownership? = Claims.take(file)

    /**
     * The ownership, waiting up to [timeout] for whoever holds it to let go, and `null` if
     * they do not within it.
     *
     * Short attempts rather than a blocking lock, because a blocking lock has no limit to
     * give and there is a caller who is not allowed to hang on one: the window waits
     * [WAIT_LIMIT] and then opens regardless. The expiry is answered, and what to do about it
     * is the caller's to decide.
     *
     * Blocking, not suspending: the window takes the ownership before the graph that would
     * give it a scope exists.
     */
    fun acquire(timeout: Duration): Ownership? {
        val deadline = TimeSource.Monotonic.markNow() + timeout
        while (true) {
            Claims.take(file)?.let { return it }
            val remaining = -deadline.elapsedNow()
            if (remaining <= Duration.ZERO) return null
            Thread.sleep(minOf(RETRY_INTERVAL, remaining).inWholeMilliseconds)
        }
    }

    companion object {

        /**
         * How long the window waits for the ownership before opening anyway.
         *
         * Ten seconds. What it waits on is a headless process finishing a single call it took
         * the ownership for, which is milliseconds of work; a call longer than this is not a
         * real case, and refusing to open the app because of a lock would be a worse answer
         * than a `Flow` that misses one update.
         */
        val WAIT_LIMIT: Duration = 10.seconds
    }
}

/**
 * A hold on the database, given back by [DatabaseOwnership.tryAcquire] and
 * [DatabaseOwnership.acquire].
 *
 * Releasing returns the kernel's lock once the last holder in this process is done with it,
 * and closes the channel it was taken on: a headless session takes and releases one of these
 * per call, and a channel left behind would spend a descriptor a call. Releasing twice is
 * releasing once.
 */
class Ownership internal constructor(private val path: String) : AutoCloseable {

    private val released = AtomicBoolean(false)

    fun release() {
        if (released.compareAndSet(false, true)) Claims.give(path)
    }

    override fun close() = release()
}

/**
 * The one claim per file this process is allowed to hold, and how many holders inside it are
 * standing on it.
 *
 * Process-wide and keyed by path, rather than state of a [DatabaseOwnership] instance: two
 * instances naming the same file are one claim to the kernel, and a second instance opening a
 * channel of its own would take the first one's lock away.
 */
private object Claims {

    private val held = mutableMapOf<String, Claim>()

    fun take(file: File): Ownership? = synchronized(this) {
        val claim = held[file.path] ?: open(file)?.also { held[file.path] = it } ?: return null
        claim.holders++
        Ownership(file.path)
    }

    fun give(path: String): Unit = synchronized(this) {
        val claim = held[path] ?: return
        claim.holders--
        if (claim.holders == 0) {
            held.remove(path)
            claim.release()
        }
    }

    /**
     * The channel and the lock on it, or nothing at all — a channel is never left open
     * without the lock it was opened for, because closing it later, after the claim was taken
     * on another one, would drop that claim.
     */
    private fun open(file: File): Claim? {
        val channel = try {
            file.parentFile?.mkdirs()
            FileChannel.open(
                file.toPath(),
                StandardOpenOption.CREATE,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
            )
        } catch (cause: IOException) {
            return null
        }
        val lock = try {
            channel.tryLock()
        } catch (cause: IOException) {
            null
        }
        if (lock == null) {
            channel.close()
            return null
        }
        return Claim(channel, lock)
    }

    private class Claim(
        private val channel: FileChannel,
        private val lock: FileLock,
    ) {
        var holders: Int = 0

        fun release() = channel.use { lock.release() }
    }
}

/**
 * The file the claim is taken on, resolved so that two spellings of one path are one claim.
 */
private fun ownershipFile(databasePath: String): File {
    val file = File(databasePath + OWNERSHIP_SUFFIX)
    return try {
        file.canonicalFile
    } catch (cause: IOException) {
        file.absoluteFile
    }
}

/**
 * What the file the claim is taken on is called, beside the archive.
 *
 * Not Room's `.lck`, which is beside it too: Room takes that one while it configures a
 * connection and gives it back by closing the channel, and that close would drop every lock
 * this process holds on that same file — including one taken here.
 */
private const val OWNERSHIP_SUFFIX = ".ownership"

/** How long a refused attempt waits before the next one. */
private val RETRY_INTERVAL = 50.milliseconds
