package com.neoutils.finsight.security

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The same primitives as the desktop, because Android offers the same ones.
 *
 * The only consumer of these today is the MCP server, which exists on the desktop alone — but
 * that is a reason to keep a platform from *weakening* a secret, never a reason to hand it
 * something weaker. `java.security` is available here, so the honest actual is the real one.
 * A target that could not meet the requirement fails loudly instead.
 */
private val secureRandom = SecureRandom()

actual fun secureRandomHex(byteCount: Int): String {
    val bytes = ByteArray(byteCount)
    secureRandom.nextBytes(bytes)
    return bytes.joinToString(separator = "") { "%02x".format(it) }
}

actual fun constantTimeEquals(expected: String, candidate: String): Boolean =
    MessageDigest.isEqual(
        expected.encodeToByteArray(),
        candidate.encodeToByteArray(),
    )
