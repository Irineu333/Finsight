package com.neoutils.finsight.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.window.core.layout.WindowSizeClass
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.feature.shell.api.ChromeConfig
import com.neoutils.finsight.feature.shell.api.LocalChromeController
import com.neoutils.finsight.feature.shell.api.NavCatalog
import com.neoutils.finsight.feature.shell.api.NavDestination as CatalogDestination
import com.neoutils.finsight.feature.transactions.api.TransactionsEntry
import com.neoutils.finsight.navigation.LocalCanNavigateBack
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.navigation.NavRoute
import com.neoutils.finsight.ui.component.BottomNavigationBar
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.NavigationRailBar
import org.koin.compose.koinInject

@Composable
fun ChromeHost(
    content: @Composable (PaddingValues) -> Unit,
) {
    val analytics = koinInject<Analytics>()
    val transactionsEntry = koinInject<TransactionsEntry>()
    val navCatalog = koinInject<NavCatalog>()
    val modalManager = LocalModalManager.current
    val navController = LocalNavController.current
    val chromeController = rememberChromeStateHolder()

    val destinations = navCatalog.destinations
    val railItems = destinations.filter { !it.mobileOnly }
    val bottomItems = destinations.filter { it.primaryTab }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val destination = currentBackStackEntry?.destination

    // The active section is the destination whose route is anywhere in the current hierarchy — this
    // keeps the selector highlighted even on sub-destinations pushed inside a section.
    val selectedItem = destinations.firstOrNull { item ->
        destination?.hierarchy?.any { it.hasRoute(item.route::class) } == true
    }

    val isOnPrimaryTab = selectedItem?.primaryTab == true

    LaunchedEffect(selectedItem) {
        if (selectedItem?.primaryTab == true) {
            analytics.logScreenView(selectedItem.screenName)
        }
    }

    // The one navigation primitive for selecting a section: preserve each section's own back stack
    // when switching, so returning to a section restores where the user left off.
    val onItemSelected: (CatalogDestination) -> Unit = { item ->
        navController.navigateToSection(item.route)
    }

    val isWideWindow = currentWindowAdaptiveInfo().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    // Back-affordance rule: a section root under the persistent desktop rail has nowhere useful to go
    // back to (the rail is the navigation); on mobile, non-tab screens and any pushed sub-destination
    // keep their back button. Depth is the count of real screens of the current section on the stack.
    val currentBackStack by navController.currentBackStack.collectAsState()
    val sectionDepth = destination?.let { current ->
        val sectionId = current.topLevelSectionId()
        currentBackStack.count { entry ->
            entry.destination !is NavGraph && entry.destination.topLevelSectionId() == sectionId
        }
    } ?: 0
    val canNavigateBack = (!isWideWindow && !isOnPrimaryTab) || sectionDepth > 1

    // Visibility, per form factor:
    // - Desktop rail: persistent — visible unless the screen publishes `ContentOnly`.
    // - Mobile bottom bar: only on a primary tab, and unless the screen publishes `ContentOnly`.
    val effectiveConfig = when {
        isWideWindow -> chromeController.config
        isOnPrimaryTab -> chromeController.config
        else -> ChromeConfig.ContentOnly
    }

    val chromeTransition = updateTransition(
        targetState = effectiveConfig,
        label = "ChromeTransition",
    )

    val onAddTransaction = {
        modalManager.show(transactionsEntry.addTransactionModal())
    }

    CompositionLocalProvider(
        LocalChromeController provides chromeController,
        LocalCanNavigateBack provides canNavigateBack,
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(),
            bottomBar = {
                if (!isWideWindow) {
                    chromeTransition.AnimatedVisibility(
                        visible = { it.isBottomBarVisible },
                        enter = slideInVertically { it } + expandVertically(),
                        exit = shrinkVertically() + slideOutVertically { it },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        BottomNavigationBar(
                            items = bottomItems,
                            selectedItem = selectedItem ?: bottomItems.first(),
                            onItemSelected = onItemSelected,
                        )
                    }
                }
            },
            floatingActionButton = {
                if (!isWideWindow) {
                    chromeTransition.AnimatedVisibility(
                        visible = { it.isFloatingActionButtonVisible },
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .offset(y = 40.dp)
                            .size(56.dp)
                    ) {
                        AddTransactionFab(onClick = onAddTransaction)
                    }
                }
            },
            floatingActionButtonPosition = FabPosition.Center,
        ) { padding ->
            Row(modifier = Modifier.fillMaxSize()) {
                if (isWideWindow) {
                    chromeTransition.AnimatedVisibility(
                        visible = { it.isBottomBarVisible },
                        enter = slideInHorizontally { -it } + expandHorizontally() + fadeIn(),
                        exit = shrinkHorizontally() + slideOutHorizontally { -it } + fadeOut(),
                    ) {
                        NavigationRailBar(
                            items = railItems,
                            selectedItem = selectedItem ?: railItems.first(),
                            onItemSelected = onItemSelected,
                            header = {
                                chromeTransition.AnimatedVisibility(
                                    visible = { it.isFloatingActionButtonVisible },
                                    enter = fadeIn(),
                                    exit = fadeOut(),
                                ) {
                                    AddTransactionFab(onClick = onAddTransaction)
                                }
                            },
                        )
                    }
                }

                content(padding)
            }
        }
    }
}

/**
 * Selects a top-level section, preserving multiple back stacks: the current section's stack is saved
 * and the target section's stack restored, so switching sections never loses in-section navigation.
 */
private fun androidx.navigation.NavController.navigateToSection(route: NavRoute) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private val CatalogDestination.screenName: String
    get() = route::class.simpleName
        .orEmpty()
        .removeSuffix("Route")
        .removeSuffix("Graph")
        .replaceFirstChar { it.lowercase() }

/**
 * The id of the top-level section graph (the direct child of the host's root graph) that this
 * destination belongs to — used to measure how deep the current section's stack is.
 */
private fun NavDestination.topLevelSectionId(): Int {
    val chain = hierarchy.toList()
    return if (chain.size >= 2) chain[chain.size - 2].id else id
}

@Composable
private fun AddTransactionFab(
    onClick: () -> Unit,
) {
    FloatingActionButton(
        onClick = onClick,
        contentColor = Color.White,
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
    }
}
