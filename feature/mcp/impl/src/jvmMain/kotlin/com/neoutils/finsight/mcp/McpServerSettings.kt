package com.neoutils.finsight.mcp

import com.neoutils.finsight.feature.mcp.api.McpPermissionAxis
import com.neoutils.finsight.feature.mcp.api.McpServerController
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.util.prefs.Preferences

/**
 * What the user decided about the server, kept where the app already keeps preferences.
 *
 * All four answers outlive the process, and each for its own reason. **Whether to run it**,
 * because a server that had to be switched on at every launch would be unusable for what it exists
 * for: the agent would connect or not according to whether the user remembered, and the failure
 * would surface on the agent's side, far from its cause. **Which port**, because the client is
 * configured against it and a port that moved would break a configuration that was already made.
 * **The token**, because it is half of that same configuration. **Which capabilities are granted**,
 * because a grant that had to be repeated every launch would be one the user stops reading.
 *
 * The token is held in clear text, deliberately. Its reach is the loopback interface of the very
 * machine it is written on, and whoever can read it already reads the database file, which is not
 * encrypted either; an operating-system vault would pull in three native dependencies to guard
 * against an attacker who has already won. The defence that matters against a local server is
 * `Host`/`Origin` validation, and that lives on the socket (design D11).
 *
 * **Construction only reads.** Minting a token is a write, and it waits for the first moment there
 * is a server to connect to — resolving the controller in a test, or on a platform that never
 * listens, must not leave a secret behind on the machine.
 *
 * **The choice crosses to another process the moment it is made.** Reading is what construction
 * does, once, and the flows answer from there on: this object is the process's view of the choice,
 * not a window onto the file. That is enough because the process that has to read a fresh choice —
 * the stdio one an agent's client launches — builds the whole of itself at every launch, seconds
 * after the user may have moved a switch. What it must not find is the previous answer, so [store]
 * is re-read before each of those reads and written through after every write (design D7).
 */
internal class McpServerSettings(
    private val settings: Settings,
    /**
     * The `java.util.prefs` node [settings] is held in, or `null` where it is held in something
     * with no disk behind it — the `Settings` a test states, and any store that is not this one.
     *
     * It is a second parameter because `Settings` has no `flush` and no `sync`: those belong to
     * `java.util.prefs`, and `PreferencesSettings` neither calls them nor hands out the node it
     * writes to. Without them, when a choice becomes visible to another process is the
     * implementation's to decide: the JVM's file-backed store commits on a timer of up to 30 s, so
     * a process launched in between reads the answer before the last one. Where the platform's own
     * daemon hands a write straight to the next reader — macOS does — the calls cost a syscall and
     * change nothing, which is the price of the requirement holding everywhere.
     */
    private val store: Preferences? = null,
) {

    private val _isEnabled = MutableStateFlow(read { settings.getBoolean(KEY_ENABLED, false) })

    /**
     * `false` where nothing was ever persisted, which is the state of an app that just gained the
     * feature: no choice was made, so nothing comes up and nothing listens.
     */
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _port = MutableStateFlow(read { settings.getInt(KEY_PORT, McpServerController.DEFAULT_PORT) })

    val port: StateFlow<Int> = _port.asStateFlow()

    private val _token = MutableStateFlow(read { settings.getStringOrNull(KEY_TOKEN) })

    val token: StateFlow<String?> = _token.asStateFlow()

    private val _permissions = MutableStateFlow(storedPermissions())

    /**
     * What the user has granted, `read` alone until they say otherwise.
     *
     * The default is per axis and not per set, so an app that ever gains a fifth axis gains it
     * withheld on every installation that already exists, rather than granted because nothing was
     * written for it yet.
     */
    val permissions: StateFlow<Set<McpPermissionAxis>> = _permissions.asStateFlow()

    fun setEnabled(enabled: Boolean) = write {
        settings.putBoolean(KEY_ENABLED, enabled)
        _isEnabled.value = enabled
    }

    fun setPort(port: Int) = write {
        settings.putInt(KEY_PORT, port)
        _port.value = port
    }

    /**
     * Grants or withholds one axis, and touches no other.
     *
     * One axis per call is the independence of the four expressed in the signature: there is no
     * shape here that could grant a second one along the way.
     */
    fun setPermission(axis: McpPermissionAxis, granted: Boolean) = write {
        settings.putBoolean(axis.settingsKey, granted)
        _permissions.value = storedPermissions()
    }

    private fun storedPermissions(): Set<McpPermissionAxis> = read {
        McpPermissionAxis.entries
            .filterTo(mutableSetOf()) { settings.getBoolean(it.settingsKey, it in McpPermissionAxis.INITIAL) }
    }

    /**
     * Answers from the store as another process left it: [Preferences.sync] pulls in what was
     * committed elsewhere before the value is read.
     */
    private fun <T> read(value: () -> T): T {
        store?.sync()
        return value()
    }

    /**
     * Applies a change and commits it: [Preferences.flush] pushes it out where a process starting
     * now will find it, rather than whenever the JDK's own timer next fires.
     */
    private fun write(change: () -> Unit) {
        change()
        store?.flush()
    }

    /**
     * The token to authorise against, minting and persisting one the first time there is a server
     * to present it to.
     */
    fun requireToken(): String = _token.value ?: mint()

    /** Mints a token over whatever was there, which is what makes the previous one stop working. */
    fun regenerateToken(): String = mint()

    private fun mint(): String {
        val bytes = ByteArray(TOKEN_BYTES).also(RANDOM::nextBytes)
        val token = bytes.joinToString(separator = "") { byte -> HEX[byte.toInt() and 0xFF] }
        write {
            settings.putString(KEY_TOKEN, token)
            _token.value = token
        }
        return token
    }

    private companion object {

        const val KEY_ENABLED = "mcp_server_enabled"
        const val KEY_PORT = "mcp_server_port"
        const val KEY_TOKEN = "mcp_server_token"

        /** Keyed off [McpPermissionAxis.key], so a grant survives a renaming of the label. */
        val McpPermissionAxis.settingsKey: String get() = "mcp_permission_$key"

        /** 256 bits, which is past guessing for a secret that is only ever offered on loopback. */
        const val TOKEN_BYTES = 32

        val RANDOM = SecureRandom()

        val HEX: List<String> = (0..0xFF).map { it.toString(radix = 16).padStart(2, '0') }
    }
}
