package com.neoutils.finsight.feature.mcp.api

/**
 * The command that launches this very program in the stdio mode, or `null` when the process cannot
 * say what it was started from.
 *
 * `jpackage` writes `-Djpackage.app-path=<binary>` into the configuration of every launcher it
 * produces, so an installed app states the path of its own executable rather than a guess assembled
 * from an installation directory: the binary inside `Finsight.app/Contents/MacOS` on macOS,
 * `Finsight.exe` on Windows, `/opt/finsight/bin/Finsight` on Linux. It is the same mechanism the
 * backup vault stamps a copy with the build that took it.
 *
 * A run started from Gradle has no launcher and therefore no such property. What a client would
 * have to launch then is this process's own command — the JVM the developer is running under —
 * which is the honest answer for a development build and never the answer for an installed one,
 * which is why the property is read first (design D9).
 *
 * Read at each call rather than remembered, so that what is reported is what the process is running
 * as and not what it was when some object happened to be built.
 */
fun McpLaunchCommand.Companion.ofThisProcess(): McpLaunchCommand? = executable()?.let { path ->
    McpLaunchCommand(command = path, args = listOf(STDIO_ARGUMENT))
}

/** The executable behind this process: the packaged launcher, or what the kernel says started it. */
private fun executable(): String? =
    System.getProperty(PACKAGED_APP_PATH)?.takeIf { it.isNotBlank() }
        ?: ProcessHandle.current().info().command().orElse(null)?.takeIf { it.isNotBlank() }

private const val PACKAGED_APP_PATH = "jpackage.app-path"
