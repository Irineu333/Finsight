package com.neoutils.finsight.mcp

import com.russhwolf.settings.PreferencesSettings
import java.util.prefs.Preferences

/**
 * A second process that does what the stdio mode does at every launch: builds [McpServerSettings]
 * from nothing and says what it read.
 *
 * It exists because the claim of design D7 is about two processes and cannot be made inside one.
 * The window keeps the choice in memory after writing it, so a test that read it back from the
 * same object would pass with no disk involved at all — the question is what a *process starting
 * now* finds, and only a process starting now can answer it.
 *
 * The node is given rather than assumed, so the test runs against preferences of its own instead
 * of the developer's.
 *
 * @param args the single argument is the name of the `java.util.prefs` node under the user root.
 */
fun main(args: Array<String>) {
    val node = Preferences.userRoot().node(args.single())
    val settings = McpServerSettings(settings = PreferencesSettings(node), store = node)

    println("enabled=${settings.isEnabled.value}")
    println("port=${settings.port.value}")
    println("permissions=${settings.permissions.value.map { it.name }.sorted().joinToString(",")}")
}
