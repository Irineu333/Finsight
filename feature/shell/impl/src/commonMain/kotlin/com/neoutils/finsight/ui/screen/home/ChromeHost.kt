@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.neoutils.finsight.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.feature.shell.api.ChromeConfig
import com.neoutils.finsight.feature.shell.api.LocalChromeController
import com.neoutils.finsight.feature.shell.api.NavCatalog
import com.neoutils.finsight.feature.shell.api.NavDestination as CatalogDestination
import com.neoutils.finsight.feature.transactions.api.TransactionsEntry
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.add_transaction_fab_description
import com.neoutils.finsight.ui.component.BottomNavigationBar
import com.neoutils.finsight.ui.component.DetailPane
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.LocalSharedTransitionScope
import com.neoutils.finsight.ui.component.NavigationRailBar
import com.neoutils.finsight.ui.component.OverlayPriority
import com.neoutils.finsight.ui.navigation.sectionOf
import com.neoutils.finsight.ui.util.isExtraWideWindow
import com.neoutils.finsight.ui.util.isWideWindow
import org.jetbrains.compose.resources.stringResource
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
    // The platform decides, in both directions: what has no desktop backing stays out of the rail,
    // and what only the desktop can run stays out of every mobile affordance.
    val railItems = destinations.filter { it.isOffered }
    val bottomItems = destinations.filter { it.primaryTab }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val destination = currentBackStackEntry?.destination

    val selectedItem = destinations.sectionOf(destination)

    val isOnPrimaryTab = selectedItem?.primaryTab == true

    LaunchedEffect(selectedItem) {
        if (selectedItem?.primaryTab == true) {
            analytics.logScreenView(selectedItem.name)
        }
    }

    // Selecting a rail/bottom-bar item jumps to a top-level feature host, resetting to the dashboard
    // root so hosts never stack — the back stack stays "dashboard → host (→ its sub-features)".
    val onItemSelected: (CatalogDestination) -> Unit = { item ->
        navController.navigate(item.route) {
            popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
            launchSingleTop = true
        }
    }

    // The one breakpoint, read from where it is declared: a screen that adapts to the rail being
    // up must be reading the very same fact the shell decided it by, and not a number that agrees.
    val isWideWindow = isWideWindow()

    // Note: the back affordance is owned by each screen (a feature's main screen hides it in wide
    // windows via `isWideWindow()`; pushed sub-destinations always show it), not propagated from here.

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

    CompositionLocalProvider(LocalChromeController provides chromeController) {
        Scaffold(
            contentWindowInsets = WindowInsets(),
            bottomBar = {
                if (!isWideWindow) {
                    chromeTransition.AnimatedVisibility(
                        visible = { it.isBottomBarVisible },
                        enter = slideInVertically { it } + expandVertically(),
                        exit = shrinkVertically() + slideOutVertically { it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aboveSharedElements(OverlayPriority.NavigationChrome),
                    ) {
                        BottomNavigationBar(
                            items = bottomItems,
                            selectedItem = selectedItem,
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
                            .aboveSharedElements(OverlayPriority.FloatingActionButton)
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
                        modifier = Modifier.aboveSharedElements(OverlayPriority.NavigationChrome),
                    ) {
                        NavigationRailBar(
                            items = railItems,
                            selectedItem = selectedItem,
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

                Box(modifier = Modifier.weight(1f)) {
                    content(padding)
                }

                if (isExtraWideWindow()) {
                    DetailPane(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(top = padding.calculateTopPadding()),
                    )
                }
            }
        }
    }
}

/**
 * Draws this chrome component in the transition overlay, above every shared element. A shared
 * element is lifted out of its containers while it animates, so nothing but this ordering keeps
 * it from being painted over the rail, the bottom bar or the FAB.
 *
 * The chrome is not a single priority block: the bar and the FAB take distinct levels from
 * [OverlayPriority], and unifying them reintroduces the defect this separation fixes. The docked
 * FAB overlaps the bottom bar, and equal priorities are broken by attach order — which for the
 * `Scaffold` slots is the reverse of the placement order, so the bar would be painted over the
 * FAB during a transition and under it at rest.
 *
 * Inert when the shell is composed outside a `SharedTransitionProvider`.
 */
@Composable
private fun Modifier.aboveSharedElements(priority: Float): Modifier {
    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return this
    return with(sharedTransitionScope) {
        this@aboveSharedElements.renderInSharedTransitionScopeOverlay(zIndexInOverlay = priority)
    }
}

@Composable
private fun AddTransactionFab(
    onClick: () -> Unit,
) {
    FloatingActionButton(
        onClick = onClick,
        contentColor = Color.White,
        modifier = Modifier.testTag("add_transaction_fab"),
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = stringResource(Res.string.add_transaction_fab_description),
            modifier = Modifier.size(24.dp)
        )
    }
}
