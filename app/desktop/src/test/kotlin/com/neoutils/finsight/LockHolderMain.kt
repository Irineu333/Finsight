package com.neoutils.finsight

import java.io.File

/**
 * The other process of [SingleInstanceGuardTest]: it claims the lock file given as its single
 * argument, announces on stdout that it holds it, and then stays alive until it is killed.
 *
 * It exists because the guarantee under test is a cross-process one. Nothing inside a single JVM
 * can stand in for a second instance of the app.
 */
fun main(args: Array<String>) {
    val lockFile = File(args.first())

    when (val outcome = SingleInstanceGuard(lockFile).tryAcquire()) {
        SingleInstanceGuard.Outcome.Acquired -> {
            println("locked")
            System.out.flush()
        }

        is SingleInstanceGuard.Outcome.Refused -> {
            println("refused: ${outcome.reason}")
            System.out.flush()
            return
        }
    }

    while (true) {
        Thread.sleep(HEARTBEAT_MILLIS)
    }
}

private const val HEARTBEAT_MILLIS = 60_000L
