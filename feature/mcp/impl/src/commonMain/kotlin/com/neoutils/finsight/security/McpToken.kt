package com.neoutils.finsight.security

/**
 * How many bytes of randomness the MCP bearer token carries — **256 bits**, comfortably above
 * the 128 the access-control specification demands as a floor. The length is fixed and public:
 * what is secret is the value, never its size.
 */
private const val MCP_TOKEN_BYTES = 32

/**
 * A fresh bearer token for the MCP server.
 *
 * The randomness is [secureRandomHex]'s, in `:core:common`, because the server that *verifies*
 * this token lives in `:app:mcp` and cannot see this module — one owner for the primitive, on
 * both sides of the comparison.
 *
 * **The result MUST NOT be logged**, sent to telemetry or written to the activity journal.
 */
internal fun generateMcpToken(): String = secureRandomHex(MCP_TOKEN_BYTES)
