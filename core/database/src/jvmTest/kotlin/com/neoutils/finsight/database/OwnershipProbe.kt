package com.neoutils.finsight.database

import kotlin.time.Duration.Companion.seconds

/**
 * A second process asking for the ownership of a database, so that the tests can ask
 * something a lock cannot be asked from inside the process holding it.
 *
 * A JDK file lock is the whole JVM's: from within the process that holds one, a second
 * attempt raises `OverlappingFileLockException` instead of being refused, so a test that
 * never leaves the process proves nothing about the exclusion this is for. This is launched
 * as its own JVM on the test classpath, and it speaks in single words:
 *
 * - `try <databasePath>` answers [ACQUIRED] or [REFUSED] and exits.
 * - `hold <databasePath>` answers [ACQUIRED] or [REFUSED], then holds until a line arrives on
 *   its stdin — or until stdin closes — answers [RELEASED] and exits.
 *
 * Anything it fails on is answered as a [FAILED] line rather than a stack trace on a stream
 * nobody is reading, so the assertion that was waiting for a word reports what went wrong.
 */
fun main(args: Array<String>) {
    try {
        val ownership = DatabaseOwnership(args[1])
        when (val command = args[0]) {
            "try" -> say(if (ownership.tryAcquire() != null) ACQUIRED else REFUSED)

            "hold" -> {
                val held = ownership.acquire(10.seconds)
                if (held == null) {
                    say(REFUSED)
                    return
                }
                say(ACQUIRED)
                readlnOrNull()
                held.release()
                say(RELEASED)
            }

            else -> say("$FAILED unknown command '$command'")
        }
    } catch (cause: Throwable) {
        say("$FAILED ${cause::class.simpleName}: ${cause.message}")
    }
}

private fun say(word: String) {
    println(word)
    System.out.flush()
}

internal const val ACQUIRED = "ACQUIRED"
internal const val REFUSED = "REFUSED"
internal const val RELEASED = "RELEASED"
internal const val FAILED = "FAILED"
