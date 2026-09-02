package com.neoutils.finsight

import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphNavigator
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.createGraph
import com.neoutils.finsight.di.appModules
import com.neoutils.finsight.feature.backup.api.BackupGraph
import com.neoutils.finsight.feature.backup.api.BackupRoute
import com.neoutils.finsight.feature.settings.api.SettingsGraph
import com.neoutils.finsight.feature.shell.api.NavCatalog
import com.neoutils.finsight.feature.shell.api.NavDestination as CatalogDestination
import com.neoutils.finsight.ui.navigation.settingsGraph
import com.neoutils.finsight.ui.screen.backupHistory.BackupHistoryRoute
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.mp.KoinPlatform
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Backup is reached from settings and has no place of its own in the navigation catalog, so
 * the chrome can only keep settings selected while a backup screen is up if those screens
 * hang from settings' graph. Nothing in the app compiles against that arrangement — the
 * graph is assembled at runtime, through `BackupEntry` resolved from Koin — so a backup
 * subgraph registered anywhere else would build, run, and leave the rail pointing at
 * nothing.
 *
 * This builds the real settings graph, over the real Koin graph, and asks it where the two
 * backup destinations ended up.
 */
class BackupSitsUnderSettingsTest {

    @BeforeTest
    fun startTheAppsKoin() {
        // The graph builder resolves the entry point through `KoinPlatform`, which reads the
        // global context — the real one, so what is registered is what the app registers.
        startKoin { modules(appModules) }
    }

    @AfterTest
    fun stopTheAppsKoin() = stopKoin()

    @Test
    fun bothBackupDestinationsHangFromTheSettingsGraph() {
        val graph = buildSettingsGraph()

        val backup = graph.everyDestination().single { it.hasRoute(BackupRoute::class) }
        val history = graph.everyDestination().single { it.hasRoute(BackupHistoryRoute::class) }

        assertTrue(backup.hierarchy.any { it.hasRoute(SettingsGraph::class) })
        assertTrue(history.hierarchy.any { it.hasRoute(SettingsGraph::class) })

        // And under their own node, not loose in settings': `BackupGraph` is what the rest of
        // the app names when it pops back to backup.
        assertTrue(backup.hierarchy.any { it.hasRoute(BackupGraph::class) })
        assertTrue(history.hierarchy.any { it.hasRoute(BackupGraph::class) })
    }

    /**
     * The defect itself, in the terms the chrome states it: `ChromeHost` selects the first
     * catalog destination the current one descends from. On a backup screen that answer used
     * to be nothing at all.
     */
    @Test
    fun theChromeResolvesSettingsAsTheSectionOfEveryBackupScreen() {
        val graph = buildSettingsGraph()
        val catalog = KoinPlatform.getKoin().get<NavCatalog>().destinations

        val backupDestinations = graph.everyDestination().filter { destination ->
            destination.hierarchy.any { it.hasRoute(BackupGraph::class) }
        }

        // The two screens plus the node they hang from.
        assertEquals(3, backupDestinations.count())
        backupDestinations.forEach { destination ->
            assertEquals(SettingsGraph, destination.selectedSection(catalog)?.route)
        }
    }

    /**
     * The one door into backup: the settings screen's tile, which calls `navigate(BackupRoute)`
     * while standing on `SettingsRoute`. The route it names now sits one level deeper — inside
     * the very graph the caller is in — so what this asks is whether the controller still
     * resolves it from there.
     */
    @Test
    fun theSettingsTilesDestinationIsStillReachableFromTheSettingsScreen() {
        val navController = navControllerOverSettings()

        navController.navigate(BackupRoute)

        assertTrue(
            navController.currentBackStackEntry?.destination?.hasRoute(BackupRoute::class) == true,
        )
    }

    /** What `ChromeHost` does to decide which rail item is lit, over the real catalog. */
    private fun NavDestination.selectedSection(
        catalog: List<CatalogDestination>,
    ): CatalogDestination? = catalog.firstOrNull { item ->
        hierarchy.any { it.hasRoute(item.route::class) }
    }

    private fun NavGraph.everyDestination(): Sequence<NavDestination> = asSequence().flatMap {
        if (it is NavGraph) sequenceOf(it) + it.everyDestination() else sequenceOf(it)
    }

    /**
     * The settings graph as `AppNavHost` builds it, minus the rest of the app. `navigation<>`
     * and `composable<>` resolve their navigator by name from the provider: the plain
     * [NavGraphNavigator] stands in for the compose one, which is internal to the library and
     * adds only the graph-level transitions — nothing this asks about.
     */
    private fun buildSettingsGraph(): NavGraph = navControllerOverSettings().graph

    private fun navControllerOverSettings(): NavHostController {
        val navController = NavHostController().apply {
            navigatorProvider.addNavigator(NavGraphNavigator(navigatorProvider))
            navigatorProvider.addNavigator(ComposeNavigator())
        }

        navController.graph = navController.createGraph(startDestination = SettingsGraph) {
            settingsGraph()
        }

        return navController
    }
}
