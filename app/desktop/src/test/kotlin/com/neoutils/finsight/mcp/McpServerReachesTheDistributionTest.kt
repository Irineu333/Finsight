package com.neoutils.finsight.mcp

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The MCP surface has to live inside the one artifact the user installs — not beside it, and
 * not only on a developer's machine.
 *
 * The failure this guards is quiet. A server dependency declared in the wrong place — a test
 * source set, or `compileOnly` — still lets the module's own tests pass and still lets the app
 * start from the IDE; only the installed app finds nothing on its classpath, and nobody runs
 * the installed app while developing.
 *
 * So the assertions read a manifest of the **distribution's** runtime classpath, written by
 * `writeDistributionManifest` from the `runtimeClasspath` configuration — the set of jars
 * `createDistributable` copies into `Finsight.app/Contents/app`, which was checked against a
 * built image and matches it exactly apart from the app's own jar. Reading that manifest
 * rather than this test's own classpath is the whole point: a `testImplementation` satisfies
 * the latter and changes nothing about what is installed.
 */
class McpServerReachesTheDistributionTest {

    private val distribution: List<Artifact> = run {
        val manifest = checkNotNull(javaClass.getResourceAsStream(MANIFEST)) {
            "The distribution manifest is missing: `processTestResources` must take it from " +
                "`writeDistributionManifest`, or this test proves nothing."
        }
        manifest.bufferedReader()
            .use { it.readLines() }
            .filter { it.isNotBlank() }
            .map { line ->
                val (origin, jar) = line.split(SEPARATOR, limit = 2)
                Artifact(origin = origin, jar = jar)
            }
    }

    @Test
    fun `the protocol implementation is packed into the desktop distribution`() {
        assertTrue(
            distribution.any { it.origin.startsWith("io.modelcontextprotocol:kotlin-sdk-server") },
            "The MCP server SDK is not in the distribution: an agent reaching the installed " +
                "app would find no server there.",
        )
    }

    @Test
    fun `the transport the server listens on is packed into the desktop distribution`() {
        assertTrue(
            distribution.any { it.origin.startsWith("io.ktor:ktor-server-cio") },
            "The server engine is not in the distribution: the SDK would have nothing to " +
                "listen on.",
        )
    }

    /**
     * The SDK and the engine arrive because the feature does, and the feature arrives because
     * the app does — one graph, one artifact. A second program shipped alongside would carry
     * them somewhere this manifest never looks.
     */
    @Test
    fun `the mcp feature ships as part of the app and not beside it`() {
        val modules = distribution.map { it.origin }

        assertTrue(
            modules.contains("project :feature:mcp:impl") &&
                modules.contains("project :feature:mcp:api"),
            "The mcp feature is not in the distribution: whatever ships, it is not this app.",
        )
    }

    private data class Artifact(val origin: String, val jar: String)

    private companion object {
        const val MANIFEST = "/distribution-classpath.txt"
        const val SEPARATOR = "|"
    }
}
