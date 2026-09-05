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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.neoutils.finsight.domain.analytics.Analytics
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
import com.neoutils.finsight.ui.navigation.sectionOf
import com.neoutils.finsight.ui.util.isExtraWideWindow
import com.neoutils.finsight.ui.util.isWideWindow
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/** The button's clearance from the edges it is not docked to. */
private val FabMargin = 16.dp

/** How far the button sinks into the bottom bar: a 56dp button keeps 32dp of itself above it. */
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
    // The platform decides, in both directions: what has no desktop backing stays out of the rail,
    // and what only the desktop can run stays out of every mobile affordance.
    val railItems = destinations.filter { it.isOffered }
    val bottomItems = destinations.filter { it.primaryTab }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val destination = currentBackStackEntry?.destination
    val destinationId = currentBackStackEntry?.id

    val selectedItem = destinations.sectionOf(destination)

    // `currentBackStackEntryAsState()` seeds itself with null and the `NavHost` that fills it is
    // composed inside this shell's content, so on the first frame the shell does not know where it
    // is. That is not the same as "this is not a primary tab".
    val isDestinationResolved = destination != null
    val isOnPrimaryTab = selectedItem?.primaryTab == true

    LaunchedEffect(selectedItem) {
        if (selectedItem?.primaryTab == true) {
            analytics.logScreenView(selectedItem.name)
        }
    }

    // Reset to the dashboard root so hosts never stack: the back stack stays
    // "dashboard → host (→ its sub-features)".
    val onItemSelected: (CatalogDestination) -> Unit = { item ->
        navController.navigate(item.route) {
            popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
            launchSingleTop = true
        }
    }

    val isWideWindow = isWideWindow()

    // The bottom bar belongs to the primary tabs; the action button does not.
    //
    // While the destination in focus has published nothing the whole answer is held, not half of
    // it: "am I on a primary tab" is already true of the destination reached while the
    // configuration is still that of the one being left, and mixing the two is a bar that starts
    // leaving a frame before the button docked into it.
    //
    // Held, though, and never inherited: a destination that never publishes would otherwise keep a
    // chrome masked for somebody else's tab-ness for as long as it is open. An empty register is
    // that case, and there the shell answers for itself.
    var lastEffectiveConfig by remember { mutableStateOf(ChromeConfig.Default) }
    val publishedConfig = chromeController.configOf(destinationId)
        ?: ChromeConfig.Default.takeIf { chromeController.isSilent }

    val effectiveConfig = when {
        publishedConfig == null -> lastEffectiveConfig
        isWideWindow || isOnPrimaryTab -> publishedConfig
        else -> publishedConfig.copy(isBottomBarVisible = false)
    }

    SideEffect { lastEffectiveConfig = effectiveConfig }

    // Re-created the moment the shell first knows where it stands, so a cold start is a painting
    // and not a change. Holding the bar back would not be enough: a child `AnimatedVisibility` seeds
    // from the parent's `currentState`, so the parent is what has to start over. The key flips once.
    val chromeTransition = key(isDestinationResolved) {
        updateTransition(
            targetState = ChromeState(effectiveConfig, isWideWindow),
            label = "ChromeTransition",
        )
    }

    // What a screen with nothing of its own to offer is served, so that it keeps the button.
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

    // The menu belongs to the destination that opened it, and to the actions it was showing.
    var isMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(destinationId, actions.size) {
        isMenuExpanded = false
    }

    // Where the rail's header put the button, so the menu — a child of the content area, which the
    // button is not — opens level with it. Subtracted rather than guessed at with a constant.
    var railButtonWindowY by remember { mutableFloatStateOf(0f) }
    var contentWindowY by remember { mutableFloatStateOf(0f) }

    // The bar's height at rest — see where it is measured. `null` until a bar has been measured,
    // which is not the same as "there is no bar".
    val density = LocalDensity.current
    var bottomBarHeight by remember { mutableStateOf<Dp?>(null) }

    val isBottomBarPresent = !isWideWindow && effectiveConfig.isBottomBarVisible
    val safeBottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()

    val cornerAnchor = safeBottom + FabMargin
    val dockedAnchor = bottomBarHeight?.let { (it - FabDockedIntoBar).coerceAtLeast(0.dp) }

    // Where the button belongs: 0f docked into the bar, 1f in the corner, `null` while the shell
    // can name no place at all. The three places and the six ways between them are in
    // `FabPlacement.kt`.
    val fabTarget: Float? = when {
        !isDestinationResolved -> null
        !isBottomBarPresent -> 1f
        else -> dockedAnchor?.let { 0f }
    }

    // The button's visibility, on a transition of its own. Asked of the chrome-wide one,
    // `currentState` answers about every chrome animation at once, and `Transition.updateTarget`
    // back-dates it to a state never reached when a second change lands mid-flight — so a button
    // visibly mid-exit could be read as gone, and `Place` would teleport it sideways. Retargeted
    // only by its own visibility, it cannot be back-dated by the bar's comings and goings.
    //
    // The width is one of its terms: from `WIDE` upwards the button over the content gives way to
    // the rail's header, and crossing the breakpoint is a disappearance like any other — one the
    // button owes an exit. And a place the shell cannot yet name is not a button either: narrowing
    // from `WIDE`, the bar is measured only in the frame after it composes, and reading `null` as
    // "no button to draw" is what leaves the entrance to play a frame later instead of never.
    val isButtonWanted = !isWideWindow &&
        effectiveConfig.actionButton.isOverContent &&
        fabTarget != null

    val fabVisibility = key(isDestinationResolved) {
        updateTransition(targetState = isButtonWanted, label = "FabVisibility")
    }

    val fabProgress = fabPlacement(
        target = fabTarget,
        isDrawn = fabVisibility.currentState,
        isWanted = isButtonWanted,
    )

    // Endpoints read directly, only the progress animated: a new bar height is not a journey.
    val bottomAnchor = lerp(dockedAnchor ?: cornerAnchor, cornerAnchor, fabProgress)
    val horizontalBias = fabProgress

    val menuAlignment = if (horizontalBias > 0.5f) Alignment.End else Alignment.CenterHorizontally

    CompositionLocalProvider(LocalChromeController provides chromeController) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                contentWindowInsets = WindowInsets(),
                bottomBar = {
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
                            // On the bar, not on the `AnimatedVisibility` above it: the child is
                            // measured at full size on every frame, so this is the resting height
                            // whatever size the animation reports upwards.
                            modifier = Modifier.onSizeChanged { size ->
                                bottomBarHeight = with(density) { size.height.toDp() }
                            },
                        )
                    }
                },
            ) { padding ->
                Row(modifier = Modifier.fillMaxSize()) {
                    chromeTransition.AnimatedVisibility(
                        visible = { it.isRailVisible },
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
                                    visible = { it.isRailButtonVisible },
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

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { contentWindowY = it.positionInWindow().y },
                    ) {
                        content(padding)

                        // The rail is a `Surface` and clips, so the menu is drawn here instead. Its
                        // scrim stops at the content area: dimming the rail would take the button's
                        // own expand control with it.
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
                                    // Level with the button, in the layout phase.
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

            // A sibling of the `Scaffold`: the scrim has to reach the bottom bar, or the bar goes on
            // taking taps behind an open menu. The button is drawn after it, for the same reason.
            if (!isWideWindow) {
                FloatingActionMenuScrim(
                    visible = isMenuExpanded,
                    onDismissRequest = { isMenuExpanded = false },
                    modifier = Modifier.aboveSharedElements(OverlayPriority.ActionScrim),
                )
            }

            // Composed at every width, and empty of everything but its own visibility whenever the
            // button is elsewhere. "Not here" has to be a state this can animate out of and into:
            // removed by a branch, the button owes the breakpoint an exit it cannot play, and
            // inserted by one it seeds itself already visible and skips the entrance. Whether it
            // has a place to be in at all is folded into that visibility, for the same reason.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                    )
                    .padding(horizontal = FabMargin)
                    .padding(bottom = bottomAnchor),
            ) {
                // How far below its own place *hidden* is, measured from wherever on the line the
                // button stands — which is what keeps the descent straight.
                //
                // Docked, the distance is the bar's own: two things on one clock descend as one
                // only if they descend the same distance. In the corner it is the button's height
                // plus the clearance it was resting at.
                val hiddenOffsetY: (Int) -> Int = { fullHeight ->
                    with(density) {
                        lerp(
                            start = bottomBarHeight ?: cornerAnchor,
                            stop = cornerAnchor + fullHeight.toDp(),
                            fraction = fabProgress,
                        ).roundToPx()
                    }
                }

                fabVisibility.AnimatedVisibility(
                    visible = { it },
                    enter = fadeIn() + slideInVertically(initialOffsetY = hiddenOffsetY),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = hiddenOffsetY),
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

/**
 * Draws this chrome component in the transition overlay, above every shared element: a shared
 * element is lifted out of its containers while it animates, and nothing else keeps it from being
 * painted over the rail, the bar or the action button.
 *
 * The three take distinct levels from [OverlayPriority] rather than one, because equal priorities
 * are broken by attach order — the chrome would depend on whether a transition happens to run.
 *
 * Inert outside a `SharedTransitionProvider`.
 */
@Composable
private fun Modifier.aboveSharedElements(priority: Float): Modifier {
    val sharedTransitionScope = LocalSharedTransitionScope.current ?: return this
    return with(sharedTransitionScope) {
        this@aboveSharedElements.renderInSharedTransitionScopeOverlay(zIndexInOverlay = priority)
    }
}
