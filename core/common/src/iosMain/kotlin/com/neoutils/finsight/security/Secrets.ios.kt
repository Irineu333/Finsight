package com.neoutils.finsight.security

/**
 * iOS has no consumer of these today: the only one is the MCP server, which exists on the
 * desktop alone, and the settings entry that leads to it is gated on that.
 *
 * **"Unreachable" is implemented as throwing, never as a lesser secret.** The temptation is a
 * `kotlin.random.Random` stand-in "since it is never called" — which would be a predictable
 * credential the day some path does call it, and it would be silent about it. Failing loudly
 * turns that mistake into a crash on the first run of the offending path, where it is cheap to
 * notice, instead of into an authentication that quietly does not authenticate.
 *
 * Reaching for a real implementation here means `SecRandomCopyBytes`, and it should be written
 * the day something on this platform actually needs a secret.
 */
private const val REASON =
    "No cryptographic secret is produced on iOS: the only consumer is the desktop MCP server."

actual fun secureRandomHex(byteCount: Int): String = throw UnsupportedOperationException(REASON)

actual fun constantTimeEquals(expected: String, candidate: String): Boolean =
    throw UnsupportedOperationException(REASON)
