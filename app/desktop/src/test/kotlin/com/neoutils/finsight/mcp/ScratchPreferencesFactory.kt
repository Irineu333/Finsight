package com.neoutils.finsight.mcp

import java.util.concurrent.ConcurrentHashMap
import java.util.prefs.AbstractPreferences
import java.util.prefs.Preferences
import java.util.prefs.PreferencesFactory

/**
 * A preference store that lives and dies with the process, chosen with
 * `-Djava.util.prefs.PreferencesFactory`.
 *
 * The graph reads preferences on its way up — the base currency, the vault's settings, the server's
 * token — and a test process that read the developer's own would take their secrets along and leave
 * behind whatever default the graph decided to write. `java.util.prefs.userRoot` is no way out of
 * that: only the file-backed implementation honours it, and on macOS the factory is
 * `MacOSXPreferences`, which does not.
 *
 * Public, with a constructor taking nothing, because `Preferences` instantiates it by name off the
 * system class loader.
 */
class ScratchPreferencesFactory : PreferencesFactory {

    override fun userRoot(): Preferences = USER

    override fun systemRoot(): Preferences = SYSTEM

    private companion object {
        val USER: Preferences = ScratchPreferences(parent = null, name = "")
        val SYSTEM: Preferences = ScratchPreferences(parent = null, name = "")
    }
}

/** One node of [ScratchPreferencesFactory]'s store: a map, and children that are more of the same. */
private class ScratchPreferences(
    parent: ScratchPreferences?,
    name: String,
) : AbstractPreferences(parent, name) {

    private val values = ConcurrentHashMap<String, String>()

    private val children = ConcurrentHashMap<String, ScratchPreferences>()

    override fun putSpi(key: String, value: String) {
        values[key] = value
    }

    override fun getSpi(key: String): String? = values[key]

    override fun removeSpi(key: String) {
        values.remove(key)
    }

    override fun removeNodeSpi() {
        values.clear()
        children.clear()
    }

    override fun keysSpi(): Array<String> = values.keys.toTypedArray()

    override fun childrenNamesSpi(): Array<String> = children.keys.toTypedArray()

    override fun childSpi(name: String): AbstractPreferences =
        children.computeIfAbsent(name) { ScratchPreferences(parent = this, name = it) }

    /** There is no store behind this one, so there is nothing to push out and nothing to pull in. */
    override fun syncSpi() = Unit

    override fun flushSpi() = Unit
}
