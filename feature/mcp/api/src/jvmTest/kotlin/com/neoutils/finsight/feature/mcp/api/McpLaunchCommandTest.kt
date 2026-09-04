package com.neoutils.finsight.feature.mcp.api

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * **What the section tells a client to launch is the executable this process is** (design D9).
 *
 * The path cannot be assembled from an installation directory guessed at, because there is no one
 * shape for it: on macOS it is a binary inside a bundle, on Windows an `.exe` in a per-user
 * installation, on Linux a file under `/opt`. `jpackage` writes the answer into the launcher it
 * produces, and reading it is the whole of the packaged case — a value shown to a user who then
 * copies it into a configuration file is one that has to be right or visibly absent, never nearly
 * right.
 *
 * The property is set and cleared here rather than observed, because a test JVM is not a packaged
 * launcher and has none; what a developer's process does have is a command of its own, which is the
 * other half of the rule.
 */
class McpLaunchCommandTest {

    private val original: String? = System.getProperty(PACKAGED_APP_PATH)

    @AfterTest
    fun tearDown() {
        if (original == null) System.clearProperty(PACKAGED_APP_PATH)
        else System.setProperty(PACKAGED_APP_PATH, original)
    }

    @Test
    fun `the packaged launcher states its own path, and that is what is published`() {
        System.setProperty(PACKAGED_APP_PATH, PACKAGED_BINARY)

        val command = assertNotNull(McpLaunchCommand.ofThisProcess())

        assertEquals(
            PACKAGED_BINARY,
            command.command,
            "The installed executable's own path was not what the client is told to launch.",
        )
        assertEquals(
            listOf("--mcp"),
            command.args,
            "The argument that makes the executable a server is not the one being published.",
        )
    }

    @Test
    fun `without a launcher the command is the one this process was started from`() {
        System.clearProperty(PACKAGED_APP_PATH)

        val command = assertNotNull(
            McpLaunchCommand.ofThisProcess(),
            "A development build published no command at all, so the section would show nothing.",
        )

        assertEquals(
            ProcessHandle.current().info().command().orElse(null),
            command.command,
            "The fallback is what launched this process, and it answered something else.",
        )
    }

    /**
     * A launcher that set the property to nothing is a launcher that did not answer, and an empty
     * `command` in a client's configuration fails at the moment the user is furthest from the
     * cause. So blank is treated as absent rather than passed on.
     */
    @Test
    fun `a blank property is not a path`() {
        System.setProperty(PACKAGED_APP_PATH, "   ")

        val command = assertNotNull(McpLaunchCommand.ofThisProcess())

        assertEquals(
            ProcessHandle.current().info().command().orElse(null),
            command.command,
            "A blank property was published as if it were the path of an executable.",
        )
    }

    private companion object {
        const val PACKAGED_APP_PATH = "jpackage.app-path"
        const val PACKAGED_BINARY = "/Applications/Finsight.app/Contents/MacOS/Finsight"
    }
}
