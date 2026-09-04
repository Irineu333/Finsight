package com.neoutils.finsight.feature.mcp.api

/**
 * How a client starts this app as a server of its own: the program to run, and what to hand it.
 *
 * Two fields rather than one string, because that is the shape a client's configuration has — a
 * `command` and its `args` — and because the argument is the entry point's word. A single path
 * would leave whoever renders it having to know which flag turns the executable into a server, and
 * there would then be two places that had to keep agreeing about it: the `main` that reads the
 * argument, and the section that prints it for the user to copy.
 *
 * Absent — `null` on [McpServerController.launchCommand] — where there is no process a client could
 * launch, which is every target but the desktop.
 */
data class McpLaunchCommand(
    /** The absolute path of the executable a client runs. */
    val command: String,
    /** What it is given, which is what makes it speak the protocol instead of opening a window. */
    val args: List<String>,
) {

    companion object {

        /**
         * The argument that puts the executable in the stdio mode.
         *
         * Stated here because the two ends of it live in modules that cannot name each other: the
         * desktop entry point matches on it to decide which of the two programs starts, and the
         * settings section shows it as part of what the user copies into a client. A second
         * spelling of it in either place would be a configuration that launches the window.
         */
        const val STDIO_ARGUMENT: String = "--mcp"
    }
}
