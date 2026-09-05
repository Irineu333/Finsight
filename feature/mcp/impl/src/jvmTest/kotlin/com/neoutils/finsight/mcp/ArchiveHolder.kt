package com.neoutils.finsight.mcp

import com.neoutils.finsight.database.DatabaseOwnership
import java.io.File

/**
 * A second process owning a database, so that a headless session can be asked what it does when
 * the window is the one holding the archive.
 *
 * It has to be a process of its own. A JDK file lock belongs to the whole JVM, and holders inside
 * one share the single claim — a test that took the ownership and then asked this session for it
 * would be handed the same claim and would prove the opposite of what it set out to.
 *
 * It says [HELD] or [REFUSED] and then holds until a line arrives on its input, or until the input
 * closes.
 */
fun main(args: Array<String>) {
    val held = DatabaseOwnership(args[0]).tryAcquire()
    println(if (held != null) HELD else REFUSED)
    System.out.flush()
    readlnOrNull()
    held?.release()
}

internal const val HELD = "HELD"
internal const val REFUSED = "REFUSED"

/** The other process, from the side of the test that launched it. */
internal class ArchiveHolder(databasePath: String) : AutoCloseable {

    private val process = ProcessBuilder(
        File(File(System.getProperty("java.home"), "bin"), "java").absolutePath,
        "-cp",
        System.getProperty("java.class.path"),
        "com.neoutils.finsight.mcp.ArchiveHolderKt",
        databasePath,
    ).redirectError(ProcessBuilder.Redirect.INHERIT).start()

    private val answers = process.inputStream.bufferedReader()
    private val orders = process.outputStream.bufferedWriter()

    fun next(): String = answers.readLine()
        ?: "the holder answered nothing and exited with ${process.waitFor()}"

    /** Tells it to let go, and waits until it has. */
    fun letGo() {
        orders.write("release")
        orders.newLine()
        orders.flush()
        process.waitFor()
    }

    override fun close() {
        process.destroyForcibly()
        process.waitFor()
    }
}
