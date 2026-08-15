package com.neoutils.finsight.security

/**
 * Random bytes from the platform's **cryptographically secure** source, rendered as
 * lowercase hexadecimal.
 *
 * It is `expect`/`actual` and not a `kotlin.random.Random` call because `kotlin.random` is
 * not a cryptographic generator: its output is predictable from previous output, and a
 * predictable secret is no secret at all. The common code states the requirement, and each
 * platform meets it with its own primitive.
 *
 * Hexadecimal, so the result survives being pasted into a configuration file, a shell
 * variable or a JSON document without escaping.
 *
 * A platform that cannot meet the requirement **fails loudly** rather than answering with
 * something weaker — a lesser secret would authenticate nothing while looking like it did.
 */
expect fun secureRandomHex(byteCount: Int): String

/**
 * Whether [candidate] equals [expected], compared in **constant time** with respect to the
 * contents.
 *
 * `==` on strings returns at the first differing character, and the time it takes is a
 * measurable function of how many leading characters were right — enough, over many attempts,
 * to recover a secret one character at a time. This comparison does not short-circuit.
 *
 * Only the *contents* are protected: a length mismatch is answered immediately, which reveals
 * the length. Use it for secrets whose length is not itself secret.
 */
expect fun constantTimeEquals(expected: String, candidate: String): Boolean
