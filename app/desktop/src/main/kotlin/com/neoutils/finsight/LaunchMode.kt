package com.neoutils.finsight

import com.neoutils.finsight.feature.mcp.api.McpLaunchCommand

/**
 * Which of the two programs inside the one executable the arguments ask for (design D1).
 *
 * The app ships a single binary, and an MCP client launches that same binary rather than a second
 * one installed beside it. So the argument list is the first thing the process reads, before a
 * window, a graph or a cloud service exists to be affected by the answer.
 *
 * **A decision separated from the `main` that acts on it**, because acting on it opens a window:
 * stated as a value, the rule can be asserted without a screen and without a display to put one on.
 */
internal enum class LaunchMode {

    /** The app the user opens: a window, and everything a window needs. */
    WINDOW,

    /** The app a client launches: the protocol on the process's own streams, and no window. */
    MCP_STDIO;

    companion object {

        /**
         * The mode the given arguments ask for.
         *
         * Anything that is not [McpLaunchCommand.STDIO_ARGUMENT] opens the window, including an
         * argument nobody recognises: a desktop launcher can be handed a file to open or a flag
         * from the operating system, and a process that refused to start over one would be an app
         * that fails to open for a reason the user cannot see.
         */
        fun of(arguments: Array<String>): LaunchMode =
            if (arguments.any { it == McpLaunchCommand.STDIO_ARGUMENT }) MCP_STDIO else WINDOW
    }
}
