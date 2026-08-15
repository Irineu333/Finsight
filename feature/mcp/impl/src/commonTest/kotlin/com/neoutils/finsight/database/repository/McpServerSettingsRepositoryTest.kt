package com.neoutils.finsight.database.repository

import com.neoutils.finsight.feature.mcp.api.McpPermission
import com.neoutils.finsight.security.constantTimeEquals
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The configuration as it is born, and what each write does — and does not — do to the rest
 * of it.
 *
 * Every case builds a *new* repository over the *same* `MapSettings` when it wants to assert
 * persistence: that is the reread, and it is the only way to tell a value that was stored from
 * one that merely lives in a `StateFlow`.
 */
class McpServerSettingsRepositoryTest {

    private val settings = MapSettings()

    private fun repository() = McpServerSettingsRepository(settings)

    @Test
    fun theDefaultsAtBirthAreOffReadOnlyAndAFixedPort() = runTest {
        val state = repository().observe().value

        assertFalse(state.isEnabled, "the server is off until the user asks for it")
        assertEquals(McpPermission.READ_ONLY, state.permission)
        assertEquals(McpServerSettingsRepository.DEFAULT_PORT, state.port)
        assertTrue(state.token.isNotBlank())
    }

    /** 256 bits, hexadecimal — well above the 128 the specification sets as the floor. */
    @Test
    fun theTokenAtBirthCarriesTheEntropyTheSpecificationDemands() = runTest {
        val token = repository().observe().value.token

        assertEquals(64, token.length)
        assertTrue(token.all { it in "0123456789abcdef" }, "token was $token")
        assertNotEquals(token, McpServerSettingsRepository(MapSettings()).observe().value.token)
    }

    @Test
    fun thePortSurvivesAReread() = runTest {
        val drawn = repository().observe().value.port

        assertEquals(drawn, repository().observe().value.port)
    }

    @Test
    fun aChosenPortSurvivesAReread() = runTest {
        repository().setPort(41234)

        assertEquals(41234, repository().observe().value.port)
    }

    @Test
    fun turningTheServerOffChangesNeitherTheTokenNorTheLevel() = runTest {
        val repository = repository()
        repository.setPermission(McpPermission.READ_WRITE)
        repository.setEnabled(true)
        val token = repository.observe().value.token

        repository.setEnabled(false)

        assertEquals(token, repository.observe().value.token)
        assertEquals(McpPermission.READ_WRITE, repository.observe().value.permission)

        val reread = repository().observe().value
        assertFalse(reread.isEnabled)
        assertEquals(token, reread.token, "a client configured before the switch still works")
        assertEquals(McpPermission.READ_WRITE, reread.permission)
    }

    @Test
    fun rotatingReplacesTheTokenAndTheOldOneDoesNotComeBack() = runTest {
        val repository = repository()
        val old = repository.observe().value.token

        val rotated = repository.rotateToken()

        assertNotEquals(old, rotated)
        assertEquals(rotated, repository.observe().value.token)
        assertEquals(rotated, repository().observe().value.token, "the new token is persisted")
        assertFalse(constantTimeEquals(expected = rotated, candidate = old))
    }

    /** Rotation is the only thing that touches the token — not disabling, not the level. */
    @Test
    fun nothingButRotationChangesTheToken() = runTest {
        val repository = repository()
        val token = repository.observe().value.token

        repository.setEnabled(true)
        repository.setPermission(McpPermission.READ_WRITE)
        repository.setPort(41235)
        repository.setEnabled(false)
        repository.setPermission(McpPermission.READ_ONLY)

        assertEquals(token, repository.observe().value.token)
    }

    @Test
    fun theTokenComparisonAcceptsTheTokenInForce() = runTest {
        val token = repository().observe().value.token

        assertTrue(constantTimeEquals(expected = token, candidate = token))
        assertFalse(constantTimeEquals(expected = token, candidate = token.dropLast(1)))
        assertFalse(constantTimeEquals(expected = token, candidate = ""))
    }
}
