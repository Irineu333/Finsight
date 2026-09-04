package com.neoutils.finsight.mcp

import com.neoutils.finsight.feature.mcp.api.McpStdioSession

/**
 * The stdio session of a platform where nothing can launch the app as a program: serving returns at
 * once, because there is no client on the other side of a pipe that does not exist.
 *
 * A stdio session is a process an agent's client starts, holds a conversation with and then ends.
 * Android and iOS have no such process — the app is started by the user through the system, and its
 * standard streams go to a log nobody speaks a protocol on — so the honest implementation there is
 * the one that never claims to be serving anybody.
 */
internal class UnavailableMcpStdioSession : McpStdioSession {

    override suspend fun serve() = Unit
}
