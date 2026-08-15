package com.neoutils.finsight.mcp.transport

import com.neoutils.finsight.security.constantTimeEquals

/** The scheme every request presents its credential under. */
private const val BEARER_SCHEME = "Bearer"

/** The realm named in the challenge, so a client can tell which credential is being asked for. */
private const val REALM = "Finsight MCP"

/**
 * Query parameter names that carry a credential in the wild. A request using any of them is
 * refused **and its token treated as compromised**, whatever the value turns out to be: a
 * secret that reached a query string has already been written to somebody's history.
 */
private val CREDENTIAL_QUERY_PARAMETERS = setOf(
    "access_token",
    "api_key",
    "apikey",
    "auth",
    "authorization",
    "bearer",
    "token",
)

/**
 * Why a request was refused. Every value answers `401`; they differ in what the log says.
 *
 * [description] is English and destined for a challenge header and a log. **It never names the
 * token, in force or offered** — a refusal that quoted the credential would be the leak the
 * refusal exists to prevent.
 */
enum class AuthRefusal(val description: String) {

    /** No authorization header at all. The challenge tells a conforming client what to send. */
    MISSING("No bearer credential was presented"),

    /** An authorization header that is not the bearer scheme, or carries no credential. */
    MALFORMED("The authorization header is not a bearer credential"),

    /** A bearer credential that is not the token in force. */
    INVALID("The bearer credential is not the token in force"),

    /**
     * A credential arrived in the query string. The revision forbids it, and this server treats
     * the token as compromised rather than merely refusing the call — see [BearerTokenAuth].
     */
    IN_QUERY_STRING("A credential in the query string is refused and its token rotated"),
}

/** The result of authenticating one request. */
sealed interface AuthResult {

    /** The request presented the token in force, in the header, under the bearer scheme. */
    data object Authenticated : AuthResult

    /** The request is refused with `401` and the challenge [BearerTokenAuth.challenge] builds. */
    data class Refused(val reason: AuthRefusal) : AuthResult
}

/**
 * The bearer credential check every request passes before anything else reads the user's data.
 *
 * **A deliberate deviation from the MCP authorization specification, declared rather than
 * implied.** That specification is optional, and conforming to it is recommended for HTTP
 * transports: it describes the server as an OAuth 2.1 resource server, with a protected resource
 * metadata document and tokens minted by an authorization server. Standing an OAuth 2.1
 * deployment up for a single-user loopback server is disproportionate to what it would protect,
 * so this server uses a static bearer token instead. What survives from the specification is the
 * part that makes a failure legible: a `401` carrying an authorization challenge that points at
 * the protected resource metadata document, so a conforming client reports "this server wants a
 * bearer token, here is where its metadata lives" rather than an opaque refusal.
 *
 * Three properties this class is responsible for, each of them a `MUST` of the access-control
 * specification:
 *
 * - **The token travels in the header, never in a query string**, where it would leak into shell
 *   history, browser history and every access log on the way. A request that carries a credential
 *   in the query string is refused *and* [onTokenCompromised] is invoked: the value is assumed
 *   burned, and rotating it is the only response that restores the guarantee. Refusing without
 *   rotating would leave a token that is already written down somewhere still valid.
 * - **The comparison does not short-circuit**, so the time a refusal takes does not tell an
 *   attacker how many leading characters were right. The primitive is `:core:common`'s
 *   [constantTimeEquals] — the same owner the generator of the token uses.
 * - **The token is never logged, never sent to telemetry and never written to the activity
 *   journal.** Nothing in this class puts [expectedToken]'s value, or the candidate's, into any
 *   message it produces; [AuthRefusal] is the whole vocabulary a refusal is described in.
 *
 * @param expectedToken the token in force, read on every call so that rotating it takes effect
 * immediately, without the server being stopped.
 * @param onTokenCompromised invoked when a credential is found in a query string. Wired to the
 * rotation the settings repository owns.
 */
class BearerTokenAuth(
    private val expectedToken: () -> String,
    private val onTokenCompromised: suspend () -> Unit = {},
) {

    /**
     * Authenticates one request.
     *
     * @param authorization the value of the authorization header, or `null` when absent.
     * @param queryParameterNames every query parameter name the request carried. Only the names
     * are needed — a credential is refused for *being* in the query string, so its value is never
     * read, compared or retained.
     */
    suspend fun authenticate(authorization: String?, queryParameterNames: Set<String>): AuthResult {
        if (queryParameterNames.any { it.lowercase() in CREDENTIAL_QUERY_PARAMETERS }) {
            onTokenCompromised()
            return AuthResult.Refused(AuthRefusal.IN_QUERY_STRING)
        }

        if (authorization == null) return AuthResult.Refused(AuthRefusal.MISSING)

        val candidate = authorization.trim().removeSchemePrefix()
            ?: return AuthResult.Refused(AuthRefusal.MALFORMED)

        return when {
            constantTimeEquals(expected = expectedToken(), candidate = candidate) -> AuthResult.Authenticated
            else -> AuthResult.Refused(AuthRefusal.INVALID)
        }
    }

    /**
     * The value of the authorization challenge sent with every `401`.
     *
     * It names the scheme, the realm and — the part that makes the refusal legible —
     * [resourceMetadataUrl], the document describing what this resource expects. `error` is
     * present only when a credential was supplied and rejected, which is what RFC 6750 asks for:
     * a request that carried nothing is told what to bring, not that what it brought was wrong.
     */
    fun challenge(resourceMetadataUrl: String, reason: AuthRefusal): String = buildString {
        append(BEARER_SCHEME)
        append(" realm=\"$REALM\"")
        if (reason != AuthRefusal.MISSING) {
            append(", error=\"invalid_token\"")
            append(", error_description=\"${reason.description}\"")
        }
        append(", resource_metadata=\"$resourceMetadataUrl\"")
    }

    /** The credential of a bearer authorization header, or `null` when it is not one. */
    private fun String.removeSchemePrefix(): String? {
        if (!startsWith("$BEARER_SCHEME ", ignoreCase = true)) return null
        return substring(BEARER_SCHEME.length + 1).trim().takeIf { it.isNotEmpty() }
    }
}
