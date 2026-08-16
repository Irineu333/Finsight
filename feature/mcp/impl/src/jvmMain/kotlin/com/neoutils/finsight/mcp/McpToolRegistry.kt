package com.neoutils.finsight.mcp

/**
 * The tools the running server is given — the single place production assembles them.
 *
 * It is one list and not a registration scattered over the tools themselves, because the closure
 * test has to be able to ask "what does this server offer?" and get an answer that is the same one
 * the socket would give. A tool that registered itself from its own file would be reachable by the
 * protocol and invisible to the question.
 *
 * **Adding to this list is half of adding a tool.** The other half is naming it in
 * [McpSurface.offered]; either edit alone fails `McpSurfaceIsClosedTest`, which is what makes a new
 * tool a decision rather than a side effect of writing one.
 *
 * Empty today: the families are built in the changes that follow, and a server with no tools still
 * speaks the protocol and answers `tools/list` with the truth about itself.
 */
internal fun mcpTools(): List<McpTool> = emptyList()
