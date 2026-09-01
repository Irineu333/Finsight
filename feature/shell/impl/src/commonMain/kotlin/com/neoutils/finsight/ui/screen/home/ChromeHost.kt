@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.neoutils.finsight.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.feature.shell.api.ActionButtonPresence
import com.neoutils.finsight.feature.shell.api.ChromeAction
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
import kotlin.math.roundToInt

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

    // `currentBackStackEntryAsState()` seeds itself with null — it is `currentBackStackEntryFlow`
    // collected with a null seed — and the `NavHost` that will fill it is composed inside this
    // shell's own content. On the first frame the shell genuinely does not know where it is, and
    // "I don't know yet" is not "this is not a primary tab". Every chrome decision below descends
    // from that difference.
    val isDestinationResolved = destination != null
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
    //
    // Until the destination in focus publishes, the chrome that is on screen stays: a destination
    // the shell has just reached has said nothing yet, which is not the same as having asked for
    // the default. Holding is also what the eye expects — chrome the screen being left and the one
    // being entered agree on should never move between them.
    var lastPublishedConfig by remember { mutableStateOf(ChromeConfig.Default) }
    val publishedConfig = chromeController.configOf(destinationId) ?: lastPublishedConfig

    SideEffect { lastPublishedConfig = publishedConfig }

    val effectiveConfig = when {
        isWideWindow || isOnPrimaryTab -> publishedConfig
        else -> publishedConfig.copy(isBottomBarVisible = false)
    }

    // Created the moment the shell first knows where it stands. A transition created *in* a state
    // does not animate towards it — `updateTransition` keeps `remember { Transition(targetState) }`,
    // so `currentState == targetState` and nothing runs. A cold start is a first painting and not a
    // change: the bar is simply there in the frame it is drawn in, and the button with it.
    //
    // Holding the bar back until the destination resolves would not be enough. A child
    // `AnimatedVisibility` takes its initial state from the parent's `currentState` at the moment it
    // is first composed, and a parent seeded on the indeterminate frame still carries "no bar"
    // there. It is the parent that has to start over.
    //
    // The key flips exactly once: the flow never emits null, and a resolved destination never
    // becomes indeterminate again.
    val chromeTransition = key(isDestinationResolved) {
        updateTransition(targetState = effectiveConfig, label = "ChromeTransition")
    }

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

    // Where the rail's header actually put the button, so that the menu opening outside the rail
    // opens level with it. Two figures read in window space and subtracted, because the menu is a
    // child of the content area and the button is not: what separates them is the rail's own
    // padding and the insets above it, which is exactly what a constant would have to guess.
    var railButtonWindowY by remember { mutableFloatStateOf(0f) }
    var contentWindowY by remember { mutableFloatStateOf(0f) }

    // The bar's height at rest. Measured on the bar itself, inside the visibility animation rather
    // than on the container the animation resizes: `expandVertically`/`shrinkVertically` measure
    // their child at full size on every frame and animate only the size they *report* upwards. Read
    // from within, the figure is therefore the resting one from the first measurement — during the
    // entrance, during the exit, and never a zero.
    //
    // That is what the docked position has to be made of. A button following the size the container
    // reports would dive to the window's edge on the way out and climb back, which is not the
    // outbound path reversed but a different path.
    //
    // `null` until a bar has been measured, which is not the same as "there is no bar".
    val density = LocalDensity.current
    var bottomBarHeight by remember { mutableStateOf<Dp?>(null) }

    val isBottomBarPresent = !isWideWindow && effectiveConfig.isBottomBarVisible
    val safeBottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()

    val cornerAnchor = safeBottom + FabMargin
    val dockedAnchor = bottomBarHeight?.let { (it - FabDockedIntoBar).coerceAtLeast(0.dp) }

    // Where the button is, as a single figure: 0f docked into the bar, 1f in the corner, and every
    // value between them a point on the one line joining the two. The horizontal bias and the
    // bottom anchor cannot arrive separately because there is only one of them — which is what
    // makes the return trip the outbound one reversed. Two independent springs would be two
    // journeys that merely start and end together.
    //
    // `null` while the shell can name no position at all: no destination resolved, or a bar the
    // button belongs to that has not been measured. Neither of those is "the corner".
    val fabTarget: Float? = when {
        !isDestinationResolved -> null
        !isBottomBarPresent -> 1f
        else -> dockedAnchor?.let { 0f }
    }

    // Seeded at the first position the shell can name, so that the button's first frame *is* that
    // position: it is placed there, not sent there. A spring seeded at a default the button never
    // occupied is precisely the two-step entrance being removed here.
    val fabPlacement = remember(fabTarget != null) { Animatable(fabTarget ?: 0f) }

    LaunchedEffect(fabPlacement, fabTarget) {
        fabTarget?.let { fabPlacement.animateTo(it) }
    }

    // Clamped: a spring is not contractually confined to [0, 1], and `lerp` extrapolates.
    val fabProgress = fabPlacement.value.coerceIn(0f, 1f)

    // The endpoints are read directly and only the progress animates: a bar of a new height, or
    // insets arriving late, change where the button rests — they are not a journey it made.
    val bottomAnchor = lerp(dockedAnchor ?: cornerAnchor, cornerAnchor, fabProgress)
    val horizontalBias = fabProgress

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
                                .aboveSharedElements(OverlayPriority.NavigationChrome),
                        ) {
                            BottomNavigationBar(
                                items = bottomItems,
                                selectedItem = selectedItem ?: bottomItems.first(),
                                onItemSelected = onItemSelected,
                                // Measured here, on the bar, and not on the modifier handed to the
                                // `AnimatedVisibility` above it: that one sits outside the
                                // enter/exit modifier and therefore sees the size the animation
                                // reports, which climbs from nothing and ends at a zero. The child
                                // is measured at full size on every frame, so this is the figure of
                                // the bar standing still, whatever the animation is doing.
                                modifier = Modifier.onSizeChanged { size ->
                                    bottomBarHeight = with(density) { size.height.toDp() }
                                },
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
                                        visible = { it.actionButton != ActionButtonPresence.Nowhere },
                                        enter = fadeIn(),
                                        exit = fadeOut(),
                                    ) {
                                        FloatingActionMenuButton(
                                            actions = actions,
                                            expanded = isMenuExpanded,
                                            onExpandedChange = { isMenuExpanded = it },
                                            modifier = Modifier.onGloballyPositioned {
                                                railButtonWindowY = it.positionInWindow().y
                                            },
                                        )
                                    }
                                },
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { contentWindowY = it.positionInWindow().y },
                    ) {
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
                                    // Level with the button, in the layout phase: the two figures
                                    // are read where they cost no recomposition, and both start at
                                    // zero — the top of the content area, which is where the menu
                                    // stood back when it was anchored to nothing.
                                    .offset {
                                        IntOffset(
                                            x = 0,
                                            y = (railButtonWindowY - contentWindowY)
                                                .roundToInt()
                                                .coerceAtLeast(0),
                                        )
                                    }
                                    .padding(start = 12.dp)
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

                // No button until there is a position to put it in. That is one or two frames of a
                // cold start, while the window itself is still arriving, and nobody sees a button
                // that is missing for two frames — whereas a button drawn in the corner and then
                // travelling to the bar is exactly the movement being removed. Any provisional
                // position is one the button must leave the moment the shell knows better, and that
                // leaving is the unasked-for animation.
                if (fabTarget != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                            )
                            .padding(horizontal = FabMargin)
                            .padding(bottom = bottomAnchor),
                    ) {
                        chromeTransition.AnimatedVisibility(
                            visible = { it.actionButton == ActionButtonPresence.Anywhere },
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
