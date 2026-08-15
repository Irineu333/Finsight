package com.neoutils.finsight.feature.mcp.api

import androidx.navigation.NavGraphBuilder

/**
 * The entry point of the `mcp` feature — **subgraph registration**, the fourth kind of
 * cross-feature access (`feature/README.md`).
 *
 * `SettingsGraph` gains this destination, and `impl ⊄ impl` means `settings:impl` cannot
 * call `NavGraphBuilder.mcpGraph()` itself: it asks this entry point for the registration
 * instead, resolved from Koin. The extension stays `internal` to `mcp:impl`, invoked only by
 * its own implementation of this interface. That is also why the destination is **not** a
 * loose `mcpGraph()` in `AppNavHost` — it belongs inside the settings graph, not beside it.
 *
 * The [NavGraphBuilder] is a **context parameter** rather than an ordinary one: the implicit
 * receiver of the surrounding `navigation<>` satisfies it, so the call site is just
 * `entry.register()`, and the compiler makes calling it outside the construction of a graph
 * unutterable. Same mechanism as `AnimatedVisibilityScope` in `:core:designsystem`.
 *
 * The `NavGraphBuilder` lambda is not `@Composable`, so whoever builds the graph resolves
 * this entry point through `KoinPlatform.getKoin()`, not `koinInject()`.
 */
interface McpEntry {
    context(builder: NavGraphBuilder)
    fun register()
}
