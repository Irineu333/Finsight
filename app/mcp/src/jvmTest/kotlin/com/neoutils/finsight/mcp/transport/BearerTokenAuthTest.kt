package com.neoutils.finsight.mcp.transport

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BearerTokenAuthTest {

    private val token = "0123456789abcdef0123456789abcdef"

    private var compromised = 0

    private val auth = BearerTokenAuth(
        expectedToken = { token },
        onTokenCompromised = { compromised++ },
    )

    @Test
    fun `admits the token in force, in the header, under the bearer scheme`() = runTest {
        assertEquals(AuthResult.Authenticated, auth.authenticate("Bearer $token", emptySet()))
    }

    @Test
    fun `the scheme is matched case-insensitively, as HTTP defines it`() = runTest {
        assertEquals(AuthResult.Authenticated, auth.authenticate("bearer $token", emptySet()))
    }

    @Test
    fun `no authorization header is refused as missing`() = runTest {
        assertEquals(AuthResult.Refused(AuthRefusal.MISSING), auth.authenticate(null, emptySet()))
    }

    @Test
    fun `another scheme is not a bearer credential`() = runTest {
        assertEquals(AuthResult.Refused(AuthRefusal.MALFORMED), auth.authenticate("Basic $token", emptySet()))
        assertEquals(AuthResult.Refused(AuthRefusal.MALFORMED), auth.authenticate("Bearer ", emptySet()))
    }

    @Test
    fun `a token that is not the one in force is refused`() = runTest {
        assertEquals(
            AuthResult.Refused(AuthRefusal.INVALID),
            auth.authenticate("Bearer 0123456789abcdef0123456789abcdee", emptySet()),
        )
    }

    @Test
    fun `the token in force is re-read on every call, so rotating takes effect at once`() = runTest {
        var current = "first"
        val rotating = BearerTokenAuth(expectedToken = { current })

        assertEquals(AuthResult.Authenticated, rotating.authenticate("Bearer first", emptySet()))
        current = "second"
        assertEquals(AuthResult.Refused(AuthRefusal.INVALID), rotating.authenticate("Bearer first", emptySet()))
        assertEquals(AuthResult.Authenticated, rotating.authenticate("Bearer second", emptySet()))
    }

    @Test
    fun `a credential in the query string is refused and the token treated as compromised`() = runTest {
        val refusal = auth.authenticate("Bearer $token", setOf("access_token"))

        assertEquals(AuthResult.Refused(AuthRefusal.IN_QUERY_STRING), refusal)
        // Refusing without revoking would leave in force a token already written to somebody's
        // history: rotation is what "treated as compromised" means.
        assertEquals(1, compromised)
    }

    @Test
    fun `every known credential parameter name burns the token, whatever its case`() = runTest {
        listOf("token", "Access_Token", "api_key", "APIKEY", "authorization", "bearer", "auth")
            .forEachIndexed { index, name ->
                assertEquals(
                    AuthResult.Refused(AuthRefusal.IN_QUERY_STRING),
                    auth.authenticate(null, setOf(name)),
                    "`$name` in a query string must be refused",
                )
                assertEquals(index + 1, compromised)
            }
    }

    @Test
    fun `an ordinary query parameter is not a credential`() = runTest {
        assertEquals(AuthResult.Authenticated, auth.authenticate("Bearer $token", setOf("cursor", "limit")))
        assertEquals(0, compromised)
    }

    @Test
    fun `the challenge points at the protected resource metadata document`() {
        val metadata = "http://127.0.0.1:7331/.well-known/oauth-protected-resource"

        val challenge = auth.challenge(metadata, AuthRefusal.MISSING)

        assertTrue(challenge.startsWith("Bearer "), challenge)
        assertTrue(challenge.contains("""resource_metadata="$metadata""""), challenge)
        // Nothing was offered, so nothing was rejected: a request that carried no credential is
        // told what to bring, not that what it brought was wrong.
        assertFalse(challenge.contains("error="), challenge)
    }

    @Test
    fun `a rejected credential is named as invalid in the challenge`() {
        val challenge = auth.challenge("http://127.0.0.1:7331/meta", AuthRefusal.INVALID)

        assertTrue(challenge.contains("""error="invalid_token""""), challenge)
        assertTrue(challenge.contains("resource_metadata="), challenge)
    }

    @Test
    fun `no refusal, and no challenge, ever carries the token`() {
        AuthRefusal.entries.forEach { reason ->
            assertFalse(reason.description.contains(token), "$reason leaks the token")
            assertFalse(auth.challenge("http://127.0.0.1:7331/meta", reason).contains(token))
        }
    }
}
