@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.neoutils.finsight.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.feature.shell.api.ChromeAction
import com.neoutils.finsight.feature.shell.api.LocalChromeController
import com.neoutils.finsight.feature.shell.api.NavCatalog
import com.neoutils.finsight.feature.shell.api.NavDestination as CatalogDestination
import com.neoutils.finsight.feature.transactions.api.TransactionsEntry
import com.neoutils.finsight.navigation.LocalNavController
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.add_transaction_fab_description
import com.neoutils.finsight.ui.component.BottomNavigationBar
import com.neoutils.finsight.ui.component.DetailPane
import com.neoutils.finsight.ui.component.FloatingActionMenu
import com.neoutils.finsight.ui.component.FloatingActionMenuButton
import com.neoutils.finsight.ui.component.FloatingActionMenuDirection
import com.neoutils.finsight.ui.component.FloatingActionMenuList
import com.neoutils.finsight.ui.component.FloatingActionMenuScrim
import com.neoutils.finsight.ui.component.LocalModalManager
import com.neoutils.finsight.ui.component.LocalSharedTransitionScope
import com.neoutils.finsight.ui.component.NavigationRailBar
import com.neoutils.finsight.ui.component.OverlayPriority
import com.neoutils.finsight.ui.util.isExtraWideWindow
import com.neoutils.finsight.ui.util.isWideWindow
import org.koin.compose.koinInject

/** The button's clearance from the edges it is not docked to. */
private val FabMargin = 16.dp

/**
 * How far the button sinks into the bottom bar. At 56dp tall it leaves 32dp of itself above the
 * bar's edge, which is the docked button this app has always drawn — the shell used to reach it by
 * offsetting the `Scaffold`'s FAB slot 40dp downwards, and this is the same figure said as what it
 * is instead of as a correction to somebody else's arithmetic.
 */
private val FabDockedIntoBar = 24.dp

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
    val destinationId = currentBackStackEntry?.id

    // The active section, resolved in two tiers so the selector highlights correctly on any screen:
    // 1. Exact route match against the hierarchy — handles section roots and sibling destinations that
    //    share a graph but are distinct rail items (e.g. Credit Cards vs Installments).
    // 2. Fallback for pushed sub-destinations (e.g. an invoice screen), whose route is not in the
    //    catalog: match the catalog item that owns the current section's start destination.
    val selectedItem = destinations.firstOrNull { item ->
        destination?.hierarchy?.any { it.hasRoute(item.route::class) } == true
    } ?: destination?.hierarchy
        ?.firstNotNullOfOrNull { it as? NavGraph }
        ?.findStartDestination()
        ?.let { sectionStart -> destinations.firstOrNull { sectionStart.hasRoute(it.route::class) } }

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

    // Two independent rules, and the reason they are stated apart. The bottom bar belongs to the
    // primary tabs; the action button does not, and is what every stacked screen keeps. A screen
    // suppresses the button by publishing it — `ContentOnly` — and never by where it sits.
    val publishedConfig = chromeController.configOf(destinationId)
    val effectiveConfig = when {
        isWideWindow || isOnPrimaryTab -> publishedConfig
        else -> publishedConfig.copy(isBottomBarVisible = false)
    }

    val chromeTransition = updateTransition(
        targetState = effectiveConfig,
        label = "ChromeTransition",
    )

    // Registering a transaction belongs to no screen — it is why the app exists — so a screen with
    // nothing of its own to offer gets it rather than losing the button. This is the one action the
    // shell knows, and it reaches it the way any feature would: by entry point.
    val universalAction = remember(transactionsEntry, modalManager) {
        listOf(
            ChromeAction(
                icon = Icons.Default.Add,
                labelRes = Res.string.add_transaction_fab_description,
                testTag = "add_transaction_fab",
                onClick = { modalManager.show(transactionsEntry.addTransactionModal()) },
            )
        )
    }

    val actions = chromeController.actionsOf(destinationId).ifEmpty { universalAction }

    // The menu belongs to the destination that opened it: navigating away closes it, and so does a
    // screen withdrawing the actions it was showing.
    var isMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(destinationId, actions.size) {
        isMenuExpanded = false
    }

    // Where the button sits, measured rather than guessed: docked into the bar when there is one,
    // and above the system's own bar when there is not — the shell zeroes the content insets, so a
    // button that inherited them would be drawn underneath that one.
    //
    // `null` until a bar has been measured at rest, which is not the same as "no bar": with no
    // figure to dock to, the button stands where it stands without one.
    val density = LocalDensity.current
    var bottomBarHeight by remember { mutableStateOf<Dp?>(null) }

    val isBottomBarPresent = !isWideWindow && effectiveConfig.isBottomBarVisible
    val safeBottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()

    val cornerAnchor = safeBottom + FabMargin
    val dockedAnchor = bottomBarHeight?.let { (it - FabDockedIntoBar).coerceAtLeast(0.dp) }

    val bottomAnchor by animateDpAsState(
        targetValue = if (isBottomBarPresent) dockedAnchor ?: cornerAnchor else cornerAnchor,
        label = "FloatingActionBottomAnchor",
    )

    // Centred over the bar, in the corner without it — the two places every action button of this
    // app already stood, now said once. Animated, because the bar comes and goes under it.
    val horizontalBias by animateFloatAsState(
        targetValue = if (isBottomBarPresent) 0f else 1f,
        label = "FloatingActionHorizontalBias",
    )

    val menuAlignment = if (horizontalBias > 0.5f) Alignment.End else Alignment.CenterHorizontally

    CompositionLocalProvider(LocalChromeController provides chromeController) {
        Box(modifier = Modifier.fillMaxSize()) {
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
                                .onSizeChanged { size ->
                                    // Only while the chrome is settled. The bar's height is a
                                    // figure of the bar at rest; its entrance animates that figure
                                    // up from nothing and its exit animates it back down, and an
                                    // anchor that believed those intermediate values would dive to
                                    // the window's edge on the way back and climb out again —
                                    // which is the return trip failing to mirror the outbound one.
                                    // The height of nothing is not a height either: the exit ends
                                    // by reporting zero, and it reports it once the transition has
                                    // already settled.
                                    if (!chromeTransition.isRunning && size.height > 0) {
                                        bottomBarHeight = with(density) { size.height.toDp() }
                                    }
                                }
                                .aboveSharedElements(OverlayPriority.NavigationChrome),
                        ) {
                            BottomNavigationBar(
                                items = bottomItems,
                                selectedItem = selectedItem ?: bottomItems.first(),
                                onItemSelected = onItemSelected,
                            )
                        }
                    }
                },
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
                                selectedItem = selectedItem ?: railItems.first(),
                                onItemSelected = onItemSelected,
                                header = {
                                    chromeTransition.AnimatedVisibility(
                                        visible = { it.isFloatingActionButtonVisible },
                                        enter = fadeIn(),
                                        exit = fadeOut(),
                                    ) {
                                        FloatingActionMenuButton(
                                            actions = actions,
                                            expanded = isMenuExpanded,
                                            onExpandedChange = { isMenuExpanded = it },
                                        )
                                    }
                                },
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        content(padding)

                        // The rail is a `Surface`, and a `Surface` clips: a menu opening inside it
                        // would be cut off by construction, so it is drawn here, beside the button
                        // and over the content. The scrim comes with it and stops at the content
                        // area — in a wide window there is no bottom bar to neutralise, and dimming
                        // the rail would take the button's own expand control with it.
                        if (isWideWindow) {
                            FloatingActionMenuScrim(
                                visible = isMenuExpanded,
                                onDismissRequest = { isMenuExpanded = false },
                                modifier = Modifier
                                    .aboveSharedElements(OverlayPriority.ActionScrim),
                            )

                            FloatingActionMenuList(
                                actions = actions,
                                expanded = isMenuExpanded,
                                onDismissRequest = { isMenuExpanded = false },
                                direction = FloatingActionMenuDirection.Beside,
                                horizontalAlignment = Alignment.Start,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp)
                                    .aboveSharedElements(OverlayPriority.FloatingActionButton),
                            )
                        }
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

            // A sibling of the `Scaffold`, and not of the content: the scrim has to reach the bottom
            // bar, or the bar would go on taking taps behind an open menu. The button and its menu
            // are drawn after it for the same reason — theirs are the only taps it must not swallow.
            if (!isWideWindow) {
                FloatingActionMenuScrim(
                    visible = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false },
                    modifier = Modifier.aboveSharedElements(OverlayPriority.ActionScrim),
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                        )
                        .padding(horizontal = FabMargin)
                        // Clamped at the use site as well: the anchor is a spring, and a spring
                        // asked to fall to zero passes through negative on the way.
                        .padding(bottom = bottomAnchor.coerceAtLeast(0.dp)),
                ) {
                    chromeTransition.AnimatedVisibility(
                        visible = { it.isFloatingActionButtonVisible },
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(BiasAlignment(horizontalBias, verticalBias = 1f))
                            .aboveSharedElements(OverlayPriority.FloatingActionButton),
                    ) {
                        FloatingActionMenu(
                            actions = actions,
                            expanded = isMenuExpanded,
                            onExpandedChange = { isMenuExpanded = it },
                            horizontalAlignment = menuAlignment,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Draws this chrome component in the transition overlay, above every shared element. A shared
 * element is lifted out of its containers while it animates, so nothing but this ordering keeps
 * it from being painted over the rail, the bottom bar or the action button.
 *
 * The chrome is not a single priority block: the bar, the scrim and the button take distinct levels
 * from [OverlayPriority], and unifying them reintroduces the defect this separation fixes. Equal
 * priorities are broken by attach order, so the chrome's appearance would depend on whether a
 * transition happens to be running.
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
