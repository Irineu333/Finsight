package com.neoutils.finsight.domain.error

/**
 * Resolving the anonymous id failed.
 *
 * There is a single case because no caller branches on why: the id is a label on
 * telemetry, so every reason — no network, a keychain the system refuses, a backend that
 * said no — is answered the same way, by recording it and going on. [cause] is what the
 * platform actually said, and the only part worth reading.
 *
 * It carries no `toUiText()` on purpose: nothing here reaches a screen. An id that failed
 * to resolve is invisible to the user, and inventing a message for it would be inventing
 * a surface that does not exist.
 */
data class AuthError(val cause: Throwable) {
    val message: String = "Could not resolve the anonymous user id"
}
