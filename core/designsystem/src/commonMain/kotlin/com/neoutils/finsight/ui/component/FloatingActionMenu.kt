package com.neoutils.finsight.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.floating_action_collapse
import com.neoutils.finsight.resources.floating_action_expand
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One offer of the app's action button.
 *
 * Same mould as [BottomNavigationItem], for the same reason: the component renders whatever
 * implements this, so it never has to know which screen — or which feature — produced the offer.
 * The label is a [StringResource] rather than a `UiText` because an action is always named by copy
 * the app ships, and because `UiText` is not part of this module's public surface.
 */
interface FloatingActionItem {
    val icon: ImageVector
    val labelRes: StringResource

    /** Locale-independent handle on this action, so an E2E flow never reaches it by its label. */
    val testTag: String

    val onClick: () -> Unit
}

/** Where the menu grows from the button that opens it. The caller anchors it; this animates it. */
enum class FloatingActionMenuDirection {
    /** Upwards, over the content — the button sits at the bottom of the window. */
    Above,

    /** Sideways, over the content — the button is the header of the navigation rail. */
    Beside,
}

/** The button, while it is the thing that opens the menu rather than an action of its own. */
const val FLOATING_ACTION_EXPAND_TEST_TAG = "floating_action_expand"

/** The area an open menu is dismissed on (see [FloatingActionMenuScrim]). */
const val FLOATING_ACTION_SCRIM_TEST_TAG = "floating_action_scrim"

/**
 * The app's action button: one plus sign, one size, on every screen.
 *
 * What it *does* is what changes with the list, and nothing about its shape says so. No action
 * draws nothing at all. A single action is the button — pressing it runs that action, and the
 * button carries that action's identity. Two or more turn it into the opener of
 * [FloatingActionMenuList], which lists them all: the button is then no action in particular, so it
 * wears the app's own add mark instead of borrowing one action's icon, and it answers to
 * [FLOATING_ACTION_EXPAND_TEST_TAG]. The plus turns into a cross while the menu is open, which is
 * the same gesture read backwards.
 *
 * The menu is [FloatingActionMenuList] and the scrim is [FloatingActionMenuScrim]: three
 * composables rather than one because the caller places them at three different points. In a wide
 * window the button is the rail's header, which clips, so the menu has to be drawn outside it; and
 * the scrim covers the whole window, so that the navigation bar stops taking taps while it is up.
 */
@Composable
fun FloatingActionMenuButton(
    actions: List<FloatingActionItem>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = actions.firstOrNull() ?: return
    val opensMenu = actions.size > 1

    val rotation by animateFloatAsState(
        targetValue = if (opensMenu && expanded) 45f else 0f,
        label = "FloatingActionRotation",
    )

    FloatingActionButton(
        onClick = {
            if (opensMenu) onExpandedChange(!expanded) else primary.onClick()
        },
        modifier = modifier.testTag(
            if (opensMenu) FLOATING_ACTION_EXPAND_TEST_TAG else primary.testTag
        ),
    ) {
        Icon(
            imageVector = if (opensMenu) Icons.Default.Add else primary.icon,
            contentDescription = when {
                !opensMenu -> stringResource(primary.labelRes)
                expanded -> stringResource(Res.string.floating_action_collapse)
                else -> stringResource(Res.string.floating_action_expand)
            },
            modifier = Modifier
                .size(24.dp)
                .rotate(rotation),
        )
    }
}

/**
 * The actions the button reveals, whenever there is more than one of them.
 *
 * It takes the same list the button does and lists it whole — the first action included. The button
 * no longer runs it, so leaving it out would put it out of reach entirely. Every item is labelled:
 * an icon alone would name nothing.
 */
@Composable
fun FloatingActionMenuList(
    actions: List<FloatingActionItem>,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    direction: FloatingActionMenuDirection,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.End,
) {
    if (actions.size < 2) return

    AnimatedVisibility(
        visible = expanded,
        enter = fadeIn() + scaleIn(initialScale = 0.8f) + when (direction) {
            FloatingActionMenuDirection.Above -> expandVertically(expandFrom = Alignment.Bottom)
            FloatingActionMenuDirection.Beside -> expandHorizontally(expandFrom = Alignment.Start)
        },
        exit = fadeOut() + scaleOut(targetScale = 0.8f) + when (direction) {
            FloatingActionMenuDirection.Above -> shrinkVertically(shrinkTowards = Alignment.Bottom)
            FloatingActionMenuDirection.Beside -> shrinkHorizontally(shrinkTowards = Alignment.Start)
        },
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = horizontalAlignment,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The first action nearest the button: opening upwards, that is the last row drawn.
            val ordered = when (direction) {
                FloatingActionMenuDirection.Above -> actions.asReversed()
                FloatingActionMenuDirection.Beside -> actions
            }

            ordered.forEach { action ->
                MenuItem(
                    action = action,
                    onDismissRequest = onDismissRequest,
                )
            }
        }
    }
}

/**
 * The press is [Surface]'s own, and not a `clickable` wrapped around it.
 *
 * Order is the whole of it: the clickable `Surface` puts the background and the clip *before* the
 * indication, so the ripple lands on top of the fill and inside the rounded corners. A `clickable`
 * modifier handed to a `Surface` from outside sits earlier in the same chain — its ripple is
 * painted under an opaque background, and the item answers a tap with nothing at all.
 */
@Composable
private fun MenuItem(
    action: FloatingActionItem,
    onDismissRequest: () -> Unit,
) {
    Surface(
        onClick = {
            onDismissRequest()
            action.onClick()
        },
        shape = MaterialTheme.shapes.large,
        color = colorScheme.surface,
        contentColor = colorScheme.onSurface,
        shadowElevation = 3.dp,
        // The clickable `Surface` sets no role of its own, and says so: this is the way it asks for
        // one.
        modifier = Modifier
            .semantics { role = Role.Button }
            .testTag(action.testTag),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = action.icon,
                // The label beside it is the name; a description would read it twice.
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )

            Text(
                text = stringResource(action.labelRes),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * What an open menu is dismissed on.
 *
 * A sibling of the whole window rather than of the button: the point is that nothing behind the
 * menu goes on taking taps, and a scrim confined to the content area would leave the navigation
 * bar clickable. Tapping it closes the menu and runs nothing.
 */
@Composable
fun FloatingActionMenuScrim(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.scrim.copy(alpha = 0.32f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                )
                .testTag(FLOATING_ACTION_SCRIM_TEST_TAG),
        )
    }
}

/**
 * The button with its menu stacked above it — the arrangement of a window whose button sits at the
 * bottom. A wide window places the two apart, and composes [FloatingActionMenuButton] and
 * [FloatingActionMenuList] itself.
 */
@Composable
fun FloatingActionMenu(
    actions: List<FloatingActionItem>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.End,
) {
    Column(
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier,
    ) {
        FloatingActionMenuList(
            actions = actions,
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            direction = FloatingActionMenuDirection.Above,
            horizontalAlignment = horizontalAlignment,
        )

        FloatingActionMenuButton(
            actions = actions,
            expanded = expanded,
            onExpandedChange = onExpandedChange,
        )
    }
}
