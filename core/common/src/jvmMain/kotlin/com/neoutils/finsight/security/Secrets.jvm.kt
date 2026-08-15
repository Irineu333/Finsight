package com.neoutils.finsight.security

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * [SecureRandom] is the JVM's cryptographic generator, seeded by the operating system.
 *
 * The no-argument constructor is deliberate. `getInstanceStrong()` can block on a machine
 * whose entropy pool is empty, and a screen that hangs would be traded for no gain: the
 * default instance is already a CSPRNG.
 */
private val secureRandom = SecureRandom()

actual fun secureRandomHex(byteCount: Int): String {
    val bytes = ByteArray(byteCount)
    secureRandom.nextBytes(bytes)
    return bytes.joinToString(separator = "") { "%02x".format(it) }
}

/**
 * `MessageDigest.isEqual` is the JDK's own constant-time comparison — it walks both arrays to
 * the end regardless of where they diverge, which is exactly the property this needs.
 */
actual fun constantTimeEquals(expected: String, candidate: String): Boolean =
    MessageDigest.isEqual(
        expected.encodeToByteArray(),
        candidate.encodeToByteArray(),
    )
