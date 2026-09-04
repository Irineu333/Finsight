package com.neoutils.finsight.mcp

import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.di.appModules
import com.neoutils.finsight.feature.mcp.api.McpServerController
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin

/**
 * The graph the headless mode brings up, in a process of its own and nothing else in it.
 *
 * `--mcp` starts from the same aggregate the window does — `startKoin(appModules)` — and resolves
 * only what the surface needs (design D5). [McpServerController] is the one binding of that surface
 * reachable from outside `feature/mcp/impl`, and resolving it builds the whole of what the mode
 * uses: the settings, the activity journal and the tool dependencies, which in turn pull the
 * repositories, the use cases and the database — and then reads from the database, because Room
 * opens the file only on the first access.
 *
 * It is a program and not a test method because the claim is about what a JVM *loads*, and only a
 * JVM launched with `-verbose:class` and nothing else on its mind can be asked that. The window's
 * own entry point is not used: it opens a window.
 */
fun main() {
    val koin = startKoin { modules(appModules) }.koin

    // Named through reflection rather than through the type, which is `internal` to the feature.
    println("resolved=${koin.get<McpServerController>()::class.java.name}")

    // Room's `build()` opens nothing: the file, the migration chain and the seeding all wait for
    // the first access. A read is what the mode does with the database, so a read is what the
    // measurement has to include — otherwise the heaviest part of a launch would go unmeasured.
    println("currencies=${runBlocking { koin.get<AppDatabase>().currencyDao().getAll().size }}")
}
