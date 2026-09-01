package com.neoutils.finsight.feature.backup.api

import androidx.navigation.NavGraphBuilder

/**
 * The backup feature's UI entry point.
 *
 * Backup is not a section of the app: it is reached from settings and holds no place in
 * the navigation catalog, so its destinations belong *inside* settings' subgraph — that is
 * what makes the chrome resolve settings as the current section while a backup screen is
 * up. Settings cannot call the feature's graph extension itself, because an `impl` never
 * sees another `impl`; it asks for the registration here.
 */
interface BackupEntry {

    /**
     * Registers the backup subgraph in the graph being built.
     *
     * [NavGraphBuilder] is a context parameter rather than an argument: the implicit
     * receiver inside `navigation<>` satisfies it, so the call site reads `entry.register()`
     * and the compiler refuses the call anywhere a graph is not under construction.
     */
    context(builder: NavGraphBuilder)
    fun register()
}
